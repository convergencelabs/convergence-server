package com.convergencelabs.server

import java.io.File

import scala.collection.JavaConverters.asScalaBufferConverter

import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.frontend.realtime.ConvergenceRealTimeFrontend
import com.convergencelabs.server.frontend.rest.ConvergenceRestFrontEnd
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
import com.orientechnologies.orient.client.remote.OServerAdmin
import com.convergencelabs.server.schema.OrientSchemaManager
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.convergencelabs.server.schema.DBType
import com.orientechnologies.orient.core.config.OGlobalConfiguration
import com.orientechnologies.orient.core.exception.OStorageException
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import java.time.Duration

object ConvergenceServerNode extends Logging {
  def main(args: Array[String]): Unit = {
    val options = ServerCLIConf(args)
    options.verify();

    val configFile = new File(options.config.get.get)

    if (!configFile.exists()) {
      error(s"Config file not found: ${configFile.getAbsolutePath}")
    } else {
      info(s"Starting up with config file: ${configFile.getAbsolutePath}")
      val config = ConfigFactory.parseFile(configFile)
      val server = new ConvergenceServerNode(config)
      server.start()
    }
  }
}

class ConvergenceServerNode(private[this] val config: Config) extends Logging {

  var nodeSystem: Option[ActorSystem] = None

  def start(): Unit = {
    val system = ActorSystem("Convergence", config)
    system.actorOf(Props[SimpleClusterListener], name = "clusterListener")

    val roles = config.getAnyRefList("akka.cluster.roles").asScala.toList

    var dbPool: Option[OPartitionedDatabasePool] = None

    if (roles.contains("backend") || roles.contains("restFrontend")) {

      val orientDbConfig = config.getConfig("convergence.orient-db")
      val baseUri = orientDbConfig.getString("db-uri")

      val convergenceDbConfig = config.getConfig("convergence.convergence-database")
      val fullUri = baseUri + "/" + convergenceDbConfig.getString("database")
      val username = convergenceDbConfig.getString("username")
      val password = convergenceDbConfig.getString("password")

      if (convergenceDbConfig.hasPath("auto-install") && convergenceDbConfig.getBoolean("auto-install")) {
        val adminUser = orientDbConfig.getString("admin-username")
        val adminPassword = orientDbConfig.getString("admin-password")
        val retryDelay = convergenceDbConfig.getDuration("retry-delay")
        bootstrapConvergenceDB(fullUri, adminUser, adminPassword, username, password, retryDelay)
      }

      dbPool = Some(new OPartitionedDatabasePool(fullUri, username, password))

      val domainStore = new DomainStore(dbPool.get)
      system.actorOf(
        DomainPersistenceManagerActor.props(baseUri, domainStore),
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

  def bootstrapConvergenceDB(uri: String, adminUser: String, adminPassword: String, username: String, password: String, retryDelay: Duration): Unit = {
    logger.info("Attempting to connect to OrientDB for the first time")
    val connectTries = Iterator.continually(attemptConnect(uri, adminUser, adminPassword, retryDelay))
    val serverAdmin = connectTries.dropWhile(_.isEmpty).next().get
    logger.info("Connected to OrientDB")

    if (!serverAdmin.existsDatabase()) {
      logger.info("Bootstrapping database")
      serverAdmin.createDatabase("document", "plocal").close()
      val db = new ODatabaseDocumentTx(uri)
      db.open(username, password)
      val schemaManager = new OrientSchemaManager(db, DBType.Convergence)

      // FIXME make this a config
      schemaManager.upgradeToVersion(2) match {
        case Success(_) =>
          logger.info("Databes bootstrapping complete")
        case Failure(f) =>
          logger.error("Databes bootstrapping failed.", f)
          db.close()
      }
    } else {
      serverAdmin.close()
    }
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
      case None         =>
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
