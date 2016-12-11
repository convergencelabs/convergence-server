package com.convergencelabs.server

import java.io.File
import java.time.Duration

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.DomainDatabaseStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.frontend.realtime.ConvergenceRealTimeFrontend
import com.convergencelabs.server.frontend.rest.ConvergenceRestFrontEnd
import com.orientechnologies.orient.client.remote.OServerAdmin
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.InitialStateAsEvents
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.UnreachableMember
import grizzled.slf4j.Logging
import com.convergencelabs.server.db.schema.ConvergenceSchemaManager
import com.convergencelabs.server.datastore.DeltaHistoryStore
import com.convergencelabs.server.datastore.DatabaseProvider
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

object ConvergenceServerNode extends Logging {
  def main(args: Array[String]): Unit = {
    try {
      val options = ServerCLIConf(args)
      val configFile = new File(options.config.toOption.get)

      if (!configFile.exists()) {
        error(s"Config file not found: ${configFile.getAbsolutePath}")
      } else {
        info(s"Starting up with config file: ${configFile.getAbsolutePath}")
        val config = ConfigFactory.parseFile(configFile)
        val server = new ConvergenceServerNode(config)
        server.start()
      }
    } catch {
      case cause: Throwable =>
        logger.error("Could not start server node", cause)
    }
  }
}

class ConvergenceServerNode(private[this] val config: Config) extends Logging {

  var nodeSystem: Option[ActorSystem] = None

  def start(): Unit = {
    val system = ActorSystem("Convergence", config)
    system.actorOf(Props[SimpleClusterListener], name = "clusterListener")

    val roles = config.getAnyRefList("akka.cluster.roles").asScala.toList

    var dbPool: Option[DatabaseProvider] = None

    if (roles.contains("backend") || roles.contains("restFrontend")) {

      val orientDbConfig = config.getConfig("convergence.orient-db")
      val baseUri = orientDbConfig.getString("db-uri")

      val convergenceDbConfig = config.getConfig("convergence.convergence-database")
      val fullUri = baseUri + "/" + convergenceDbConfig.getString("database")

      val username = convergenceDbConfig.getString("username")
      val password = convergenceDbConfig.getString("password")

      if (convergenceDbConfig.hasPath("auto-install")) {
        if (convergenceDbConfig.getBoolean("auto-install.enabled")) {
          bootstrapConvergenceDB(fullUri, convergenceDbConfig, orientDbConfig)
        }
      }

      dbPool = Some(DatabaseProvider(new OPartitionedDatabasePool(fullUri, username, password)))

      val domainDatabaseStore = new DomainDatabaseStore(dbPool.get)
      system.actorOf(
        DomainPersistenceManagerActor.props(baseUri, domainDatabaseStore),
        DomainPersistenceManagerActor.RelativePath)
    }

    if (roles.contains("backend")) {
      info("Starting up backend node.")
      val backend = new BackendNode(system, dbPool.get)
      backend.start()
    }

    if (roles.contains("realTimeFrontend")) {
      info("Starting up realtime front end.")
      val host = config.getString("convergence.websocket.host")
      val port = config.getInt("convergence.websocket.port")
      val realTimeFrontEnd = new ConvergenceRealTimeFrontend(system, host, port)
      realTimeFrontEnd.start()
    }

    if (roles.contains("restFrontend")) {
      info("Starting up rest front end.")
      val host = config.getString("convergence.rest.host")
      val port = config.getInt("convergence.rest.port")
      val restFrontEnd = new ConvergenceRestFrontEnd(system, host, port, dbPool.get)
      restFrontEnd.start()
    }

    this.nodeSystem = Some(system)
  }

  def bootstrapConvergenceDB(
    uri: String,
    convergenceDbConfig: Config,
    orientDbConfig: Config): Try[Unit] = Try {
    logger.info("Attempting to connect to OrientDB for the first time")

    val username = convergenceDbConfig.getString("username")
    val password = convergenceDbConfig.getString("password")
    val adminUsername = convergenceDbConfig.getString("admin-username")
    val adminPassword = convergenceDbConfig.getString("admin-password")
    val preRelease = convergenceDbConfig.getBoolean("auto-install.pre-release")
    val retryDelay = convergenceDbConfig.getDuration("retry-delay")

    val serverAdminUsername = orientDbConfig.getString("admin-username")
    val serverAdminPassword = orientDbConfig.getString("admin-password")

    val connectTries = Iterator.continually(attemptConnect(uri, serverAdminUsername, serverAdminPassword, retryDelay))
    val serverAdmin = connectTries.dropWhile(_.isEmpty).next().get
    logger.info("Connected to OrientDB with Server Admin")
    logger.info("Checking for convergence database")
    if (!serverAdmin.existsDatabase()) {
      logger.info("Covergence database does not exists.  Creating.")
      serverAdmin.createDatabase("document", "plocal").close()
      logger.info("Covergence database created, connecting as default admin user")

      val db = new ODatabaseDocumentTx(uri)
      db.open("admin", "admin")
      logger.info("Connected to convergence database.")

      logger.info("Deleting default 'reader' user.")
      db.getMetadata().getSecurity().getUser("reader").getDocument().delete()

      logger.info("Setting 'writer' user credentials.")
      val writerUser = db.getMetadata().getSecurity().getUser("writer")
      writerUser.setName(username)
      writerUser.setPassword(password)
      writerUser.save()

      logger.info("Setting 'admin' user credentials.")
      val adminUser = db.getMetadata().getSecurity().getUser("admin")
      adminUser.setName(adminUsername)
      adminUser.setPassword(adminPassword)
      adminUser.save()

      logger.info("Installing schema.")
      val dbProvider = DatabaseProvider(db)
      val deltaHistoryStore = new DeltaHistoryStore(dbProvider)
      dbProvider.tryWithDatabase { db =>
        val schemaManager = new ConvergenceSchemaManager(db, deltaHistoryStore, preRelease)
        schemaManager.install()
        logger.info("Schema installation complete")
      }.get

      dbProvider.shutdown()
    } else {
      logger.info("Convergence database exists.")
      serverAdmin.close()
    }
    ()
  }

  def attemptConnect(uri: String, adminUser: String, adminPassword: String, retryDelay: Duration) = {
    Try(new OServerAdmin(uri).connect(adminUser, adminPassword)) match {
      case Success(serverAdmin) => Some(serverAdmin)
      case Failure(e) => {
        logger.warn(s"Unable to connect to OrientDB, retrying in ${retryDelay.toMillis()}ms")
        Thread.sleep(retryDelay.toMillis())
        None
      }
    }
  }

  def stop(): Unit = {
    nodeSystem match {
      case Some(system) => system.terminate()
      case None =>
    }
  }

}

private class SimpleClusterListener extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive: Receive = {
    case MemberUp(member) => {
      log.debug(s"Member with role '${member.roles}' is Up: ${member.address}")
    }
    case UnreachableMember(member) => {
      log.debug("Member detected as unreachable: {}", member)
    }
    case MemberRemoved(member, previousStatus) => {
      log.debug("Member is Removed: {} after {}", member.address, previousStatus)
    }
    case _: MemberEvent => // ignore
  }
}
