package com.convergencelabs.server

import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.apache.logging.log4j.LogManager

import com.convergencelabs.server.db.PooledDatabaseProvider
import com.convergencelabs.server.db.SingleDatabaseProvider
import com.convergencelabs.server.datastore.convergence.DeltaHistoryStore
import com.convergencelabs.server.datastore.convergence.DomainDatabaseStore
import com.convergencelabs.server.datastore.convergence.PermissionsStore
import com.convergencelabs.server.datastore.convergence.PermissionsStore.Permission
import com.convergencelabs.server.datastore.convergence.PermissionsStore.Role
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.db.schema.ConvergenceSchemaManager
import com.convergencelabs.server.domain.DomainActorSharding
import com.convergencelabs.server.domain.chat.ChatChannelSharding
import com.convergencelabs.server.domain.model.RealtimeModelSharding
import com.convergencelabs.server.domain.rest.RestDomainActorSharding
import com.convergencelabs.server.frontend.realtime.ConvergenceRealTimeFrontend
import com.convergencelabs.server.frontend.rest.ConvergenceRestFrontEnd
import com.convergencelabs.server.util.SystemOutRedirector
import com.orientechnologies.orient.core.db.ODatabasePool
import com.orientechnologies.orient.core.db.ODatabaseType
import com.orientechnologies.orient.core.db.OrientDB
import com.orientechnologies.orient.core.db.OrientDBConfig
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Address
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.InitialStateAsEvents
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.UnreachableMember
import grizzled.slf4j.Logging
import com.convergencelabs.server.db.ConnectedSingleDatabaseProvider
import com.convergencelabs.server.domain.activity.ActivityActorSharding

object ConvergenceServerNode extends Logging {

  object Roles {
    val Backend = "backend"
    val RestFrontend = "restFrontend"
    val RealtimeFrontend = "realtimeFrontend"
  }

  object Environment {
    val ServerRoles = "SERVER_ROLES"
    val ClusterSeedNodes = "CLUSTER_SEED_NODES"
    val Log4jConfigFile = "LOG4J_CONFIG_FILE"
  }

  object AkkaConfig {
    val AkkaClusterRoles = "akka.cluster.roles"
    val AkkaClusterSeedNodes = "akka.cluster.seed-nodes"
  }

  val ActorSystemName = "Convergence"

  var server: Option[ConvergenceServerNode] = None

  def main(args: Array[String]): Unit = {
    SystemOutRedirector.setOutAndErrToLog();

    scala.sys.addShutdownHook {
      logger.info("JVM Shutdown Hook Invoked, stopping services")
      this.stop()
    }

    (for {
      _ <- configureLogging()
      configFile <- getConfigFile(args)
      config <- preprocessConfig(ConfigFactory.parseFile(configFile))
      _ <- validateSeedNodes(config)
      _ <- validateRoles(config)
    } yield {
      server = Some(new ConvergenceServerNode(config).start())
    }).recover {
      case cause: Throwable =>
        logger.error("Could not start Convergence Server Node", cause)
    }
  }

  private[this] def stop(): Unit = {
    server.foreach(_.stop())
    LogManager.shutdown()
  }

  private[this] def getConfigFile(args: Array[String]): Try[File] = {
    Try {
      val options = ServerCLIConf(args)
      new File(options.config.toOption.get)
    } flatMap { configFile =>
      if (!configFile.exists()) {
        Failure(new IllegalArgumentException(s"Config file not found: ${configFile.getAbsolutePath}."))
      } else {
        info(s"Using config file: ${configFile.getAbsolutePath}")
        Success(configFile)
      }
    }
  }

  private[this] def preprocessConfig(config: Config): Try[Config] = {
    // This includes the reference.conf with the defaults.
    val preProcessed = preProcessRoles(preprocessSeedNodes(config))
    val loaded = ConfigFactory.load(preProcessed)
    Success(loaded)
  }

  private[this] def preprocessSeedNodes(configFile: Config): Config = {
    val configuredSeeds = configFile.getAnyRefList(AkkaConfig.AkkaClusterSeedNodes).asScala.toList
    if (configuredSeeds.isEmpty) {
      logger.debug("No seed nodes specified in the akka config. Looking for an environment variable")
      Option(System.getenv().get(Environment.ClusterSeedNodes)) match {
        case Some(seedNodesEnv) =>
          val seedNodes = seedNodesEnv.split(",").toList
          val seedsAddresses = seedNodes.map { hostname =>
            Address("akka.tcp", ConvergenceServerNode.ActorSystemName, hostname.trim, 2551).toString
          }
          configFile.withValue(AkkaConfig.AkkaClusterSeedNodes, ConfigValueFactory.fromIterable(seedsAddresses.asJava))

        case None =>
          configFile
      }
    } else {
      configFile
    }
  }

  private[this] def preProcessRoles(configFile: Config): Config = {
    val rolesInConfig = configFile.getStringList("akka.cluster.roles").asScala.toList
    if (rolesInConfig.isEmpty) {
      Option(System.getenv().get(Environment.ServerRoles)) match {
        case Some(rolesEnv) =>
          val roles = rolesEnv.split(",").toList map (_.trim)
          val updated = configFile.withValue("akka.cluster.roles", ConfigValueFactory.fromIterable(roles.asJava))
          updated
        case None =>
          configFile
      }
    } else {
      configFile
    }
  }

  private[this] def validateRoles(config: Config): Try[Unit] = {
    val roles = config.getStringList(ConvergenceServerNode.AkkaConfig.AkkaClusterRoles).asScala.toList
    if (roles.isEmpty) {
      Failure(
        new IllegalStateException("No cluster roles were defined. " +
          s"Cluster roles must be defined in either the config '${ConvergenceServerNode.AkkaConfig.AkkaClusterRoles}s', " +
          s"or the environment variable '${ConvergenceServerNode.Environment.ServerRoles}'"))

    } else {
      Success(())
    }
  }

  private[this] def validateSeedNodes(config: Config): Try[Unit] = {
    val configuredSeeds = config.getAnyRefList(ConvergenceServerNode.AkkaConfig.AkkaClusterSeedNodes).asScala.toList
    if (configuredSeeds.isEmpty) {
      Failure(new IllegalStateException("No akka cluster seed nodes specified." +
        s"seed nodes must be specificed in the akka config '${ConvergenceServerNode.AkkaConfig.AkkaClusterSeedNodes}' " +
        s"or the environment variable '${ConvergenceServerNode.Environment.ClusterSeedNodes}"))
    } else {
      Success(())
    }
  }

  def configureLogging(): Try[Unit] = {
    Try {
      Option(System.getenv().get(Environment.Log4jConfigFile)) match {
        case Some(path) =>
          info(s"${Environment.Log4jConfigFile} is set. Attempting to load logging config from: ${path}")
          val context = LogManager.getContext(false).asInstanceOf[org.apache.logging.log4j.core.LoggerContext]
          val file = new File(path)
          if (file.canRead()) {
            info(s"Config file exists. Loading")
            // this will force a reconfiguration
            context.setConfigLocation(file.toURI());
          } else {
            warn("Log4j config file '${path}' does not exist. Ignoring.")
          }
        case None =>
      }
    }
  }
}

class ConvergenceServerNode(private[this] val config: Config) extends Logging {

  import ConvergenceServerNode.Roles._

  private[this] var system: Option[ActorSystem] = None
  private[this] var cluster: Option[Cluster] = None
  private[this] var backend: Option[BackendNode] = None
  private[this] var rest: Option[ConvergenceRestFrontEnd] = None
  private[this] var realtime: Option[ConvergenceRealTimeFrontend] = None

  def start(): ConvergenceServerNode = {
    info("Convergence Server Node starting up...")

    val system = ActorSystem(ConvergenceServerNode.ActorSystemName, config)
    this.system = Some(system)

    val cluster = Cluster(system)
    this.cluster = Some(cluster)

    system.actorOf(Props(new ClusterListener(cluster)), name = "clusterListener")

    val roles = config.getStringList(ConvergenceServerNode.AkkaConfig.AkkaClusterRoles).asScala.toList
    info(s"Convergnece Server Roles: ${roles.mkString(", ")}")

    if (roles.contains(Backend)) {
      val orientDbConfig = config.getConfig("convergence.orient-db")
      val baseUri = orientDbConfig.getString("db-uri")

      val convergenceDbConfig = config.getConfig("convergence.convergence-database")
      val convergenceDatabase = convergenceDbConfig.getString("database")

      val username = convergenceDbConfig.getString("username")
      val password = convergenceDbConfig.getString("password")

      // TODO this only works is there is one ConvergenceServerNode with
      // backend. This is fine for development, which is the only place this
      // should exist, but it would be nice to do this elsewhere.
      if (convergenceDbConfig.hasPath("auto-install")) {
        if (convergenceDbConfig.getBoolean("auto-install.enabled")) {
          bootstrapConvergenceDB(baseUri, convergenceDatabase, convergenceDbConfig, orientDbConfig) recover {
            case cause: Exception =>
              logger.error("Could not bootstrap database", cause)
          }
        }
      }

      val orientDb = new OrientDB(baseUri, OrientDBConfig.defaultConfig())

      // FIXME figure out how to set the pool size
      val dbProvider = new PooledDatabaseProvider(baseUri, convergenceDatabase, username, password)
      dbProvider.connect().get

      val domainDatabaseStore = new DomainDatabaseStore(dbProvider)
      system.actorOf(
        DomainPersistenceManagerActor.props(baseUri, domainDatabaseStore),
        DomainPersistenceManagerActor.RelativePath)

      if (roles.contains("backend")) {
        info("Role 'backend' configured on node, starting up backend.")
        val backend = new BackendNode(system, dbProvider)
        backend.start()
        this.backend = Some(backend)
      }
    } else if (roles.contains(RestFrontend) || roles.contains(RealtimeFrontend)) {
      // TODO Re-factor This to some setting in the config
      val shards = 100
      DomainActorSharding.startProxy(system, shards)
      RealtimeModelSharding.startProxy(system, shards)
      ChatChannelSharding.startProxy(system, shards)
      RestDomainActorSharding.startProxy(system, shards)
      ActivityActorSharding.startProxy(system, shards)
    }

    if (roles.contains(RestFrontend)) {
      info("Role 'restFronend' configured on node, starting up rest front end.")
      val host = config.getString("convergence.rest.host")
      val port = config.getInt("convergence.rest.port")
      val restFrontEnd = new ConvergenceRestFrontEnd(system, host, port)
      restFrontEnd.start()
      this.rest = Some(restFrontEnd)
    }

    if (roles.contains(RealtimeFrontend)) {
      info("Role 'realtimeFrontend' configured on node, starting up realtime front end.")
      val host = config.getString("convergence.realtime.host")
      val port = config.getInt("convergence.realtime.port")
      val realTimeFrontEnd = new ConvergenceRealTimeFrontend(system, host, port)
      realTimeFrontEnd.start()
      this.realtime = Some(realTimeFrontEnd)
    }

    this
  }

  private[this] def bootstrapConvergenceDB(
    uri: String,
    convergenceDatabase: String,
    convergenceDbConfig: Config,
    orientDbConfig: Config): Try[Unit] = Try {
    logger.info("auto-install is configured, attempting to connect to OrientDB to determin if the convergence database is installed.")

    val username = convergenceDbConfig.getString("username")
    val password = convergenceDbConfig.getString("password")
    val adminUsername = convergenceDbConfig.getString("admin-username")
    val adminPassword = convergenceDbConfig.getString("admin-password")
    val preRelease = convergenceDbConfig.getBoolean("auto-install.pre-release")
    val retryDelay = convergenceDbConfig.getDuration("retry-delay")

    val serverAdminUsername = orientDbConfig.getString("admin-username")
    val serverAdminPassword = orientDbConfig.getString("admin-password")

    val connectTries = Iterator.continually(attemptConnect(uri, serverAdminUsername, serverAdminPassword, retryDelay))
    val orientDb = connectTries.dropWhile(_.isEmpty).next().get

    logger.info("Checking for convergence database")
    if (!orientDb.exists(convergenceDatabase)) {
      logger.info("Covergence database does not exists.  Creating.")
      orientDb.create(convergenceDatabase, ODatabaseType.PLOCAL)
      logger.info("Covergence database created, connecting as default admin user")

      val db = orientDb.open(convergenceDatabase, "admin", "admin")
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
      val dbProvider = new ConnectedSingleDatabaseProvider(db)
      val deltaHistoryStore = new DeltaHistoryStore(dbProvider)
      dbProvider.tryWithDatabase { db =>
        val schemaManager = new ConvergenceSchemaManager(db, deltaHistoryStore, preRelease)
        schemaManager.install()
        logger.info("Schema installation complete")
      }.get

      val permissionsStore = new PermissionsStore(dbProvider)

      // Create Permissions
      permissionsStore.createPermission(Permission("domain-access", "Domain Access", "Allows a user to access a domain"))
      permissionsStore.createPermission(Permission("manage-permissions", "Manage Permissions", "Allows a user to manage permissions and roles"))

      // Create Roles
      permissionsStore.createRole(Role("admin", List("domain-access", "manage-permissions"), "Domain Administrator"))
      permissionsStore.createRole(Role("developer", List("domain-access"), "Domain Developer"))

      dbProvider.shutdown()
    } else {
      logger.info("Convergence database already exists.")
      orientDb.close()
    }
    ()
  }

  private[this] def attemptConnect(uri: String, adminUser: String, adminPassword: String, retryDelay: Duration): Option[OrientDB] = {
    info(s"Attempting to connect to OrientDB at uri: ${uri}")

    Try(new OrientDB(uri, adminUser, adminPassword, OrientDBConfig.defaultConfig())) match {
      case Success(orientDb) =>
        logger.info("Connected to OrientDB with Server Admin")
        Some(orientDb)
      case Failure(e) =>
        logger.error(s"Unable to connect to OrientDB, retrying in ${retryDelay.toMillis()}ms", e)
        Thread.sleep(retryDelay.toMillis())
        None
    }
  }

  def stop(): Unit = {
    logger.info(s"Stopping the Convergence Server Node")

    this.backend.foreach(backend => backend.stop())
    this.rest.foreach(rest => rest.stop())
    this.realtime.foreach(realtime => realtime.stop())

    logger.info(s"Leaving the cluster.")
    cluster.foreach(c => c.leave(c.selfAddress))

    system foreach { s =>
      logger.info(s"Terminating actor system.")
      s.terminate()
      Await.result(s.whenTerminated, FiniteDuration(10, TimeUnit.SECONDS))
      logger.info(s"Actor system terminated.")
    }
  }
}

private class ClusterListener(cluster: Cluster) extends Actor with ActorLogging {
  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive: Receive = {
    case MemberUp(member) =>
      log.debug(s"Member with role '${member.roles}' is Up: ${member.address}")
    case UnreachableMember(member) =>
      log.debug("Member detected as unreachable: {}", member)
    case MemberRemoved(member, previousStatus) =>
      log.debug("Member is Removed: {} after {}", member.address, previousStatus)
    case msg: MemberEvent =>
      log.debug(msg.toString)
  }
}
