package com.convergencelabs.server

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Address, Props}
import akka.cluster.Cluster
import com.convergencelabs.server.api.realtime.ConvergenceRealtimeApi
import com.convergencelabs.server.api.rest.ConvergenceRestApi
import com.convergencelabs.server.datastore.convergence._
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.db.PooledDatabaseProvider
import com.convergencelabs.server.domain.DomainActorSharding
import com.convergencelabs.server.domain.activity.ActivityActorSharding
import com.convergencelabs.server.domain.chat.ChatSharding
import com.convergencelabs.server.domain.model.RealtimeModelSharding
import com.convergencelabs.server.domain.rest.RestDomainActorSharding
import com.convergencelabs.server.util.SystemOutRedirector
import com.orientechnologies.orient.core.db.{OrientDB, OrientDBConfig}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import grizzled.slf4j.Logging
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters.{asScalaBufferConverter, seqAsJavaListConverter}
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

/**
 * This is the main entry point for the Convergence Server. It contains the
 * main method that will launch the server. Each instance of the Convergence
 * Server can run one or more of the three Server Roles that the Convergence
 * Server supports.  These are 1) the `backend` role which contains the core
 * business logic of Convergence, 2) the `restApi` role which serves the
 * Convergence Rest API, and 3) the `realtimeApi` role which serves the
 * Convergence Realtime API. These roles are separated out such that, when
 * clustered, the various roles of the system can be horizontally scaled
 * independently.
 */
object ConvergenceServer extends Logging {

  /**
   * Defines the string constants for the Convergence Server roles.
   */
  object Roles {
    val Backend = "backend"
    val RestApi = "restApi"
    val RealtimeApi = "realtimeApi"
  }

  /**
   * String constants for the environment variables that the Convergence Server
   * will look for when initializing.
   */
  object Environment {
    val ServerRoles = "SERVER_ROLES"
    val ClusterSeedNodes = "CLUSTER_SEED_NODES"
    val Log4jConfigFile = "LOG4J_CONFIG_FILE"
  }

  /**
   * String constants for various Akka baseConfig keys that are used during
   * initialization.
   */
  object AkkaConfig {
    val AkkaClusterRoles = "akka.cluster.roles"
    val AkkaClusterSeedNodes = "akka.cluster.seed-nodes"
  }

  /**
   * The name of the Akka ActorSystem.
   */
  private val ActorSystemName = "Convergence"

  /**
   * The currently running instance of the ConvergenceServer.
   */
  private var server: Option[ConvergenceServer] = None

  /**
   * The main entry point of the ConvergenceServer.
   *
   * @param args Command line arguments.
   * @see [[ConvergenceServerCLIConf]]
   */
  def main(args: Array[String]): Unit = {
    SystemOutRedirector.setOutAndErrToLog()

    scala.sys.addShutdownHook {
      logger.info("JVM Shutdown Hook invoked, stopping services")
      this.stop()
    }

    (for {
      _ <- configureLogging()
      configFile <- getConfigFile(args)
      config <- preprocessConfig(ConfigFactory.parseFile(configFile))
      _ <- validateSeedNodes(config)
      _ <- validateRoles(config)
    } yield {
      server = Some(new ConvergenceServer(config).start())
    }).recover {
      case cause: Throwable =>
        logger.error("Could not start Convergence Server", cause)
    }
  }

  /**
   * Helper method that will shut down the server, if it was started.
   */
  private[this] def stop(): Unit = {
    server.foreach(_.stop())
    LogManager.shutdown()
  }

  /**
   * Attempts to load the configuration file, as specified by the command
   * line arguments.
   * @param args The command line arguments supplied to the main method.
   * @return The File reference to the baseConfig file if it exists.
   */
  private[this] def getConfigFile(args: Array[String]): Try[File] = {
    Try {
      val options = ConvergenceServerCLIConf(args)
      new File(options.config.toOption.get)
    } flatMap { configFile =>
      if (!configFile.exists()) {
        Failure(new IllegalArgumentException(s"Config file not found: ${configFile.getAbsolutePath}."))
      } else {
        info(s"Using baseConfig file: ${configFile.getAbsolutePath}")
        Success(configFile)
      }
    }
  }

  /**
   * A helper method that will integrate the Akka / Lightbend baseConfig file with
   * command line arguments and environment variables.
   * @param baseConfig The loaded Config file.
   * @return The merged Config object.
   */
  private[this] def preprocessConfig(baseConfig: Config): Try[Config] = {
    // This includes the reference.conf with the defaults.
    val preProcessed = mergeServerRoles(mergeSeedNodes(baseConfig))
    val loaded = ConfigFactory.load(preProcessed)
    Success(loaded)
  }

  /**
   * Merges the Akka Cluster Seed Nodes, with those specified in the environment variable.
   * @param baseConfig The original Config object.
   * @return The merged Config object.
   */
  private[this] def mergeSeedNodes(baseConfig: Config): Config = {
    val configuredSeeds = baseConfig.getAnyRefList(AkkaConfig.AkkaClusterSeedNodes).asScala.toList
    if (configuredSeeds.isEmpty) {
      logger.debug("No seed nodes specified in the akka baseConfig. Looking for an environment variable")
      Option(System.getenv().get(Environment.ClusterSeedNodes)) match {
        case Some(seedNodesEnv) =>
          val seedNodes = seedNodesEnv.split(",").toList
          val seedsAddresses = seedNodes.map { hostname =>
            Address("akka.tcp", ConvergenceServer.ActorSystemName, hostname.trim, 2551).toString
          }
          baseConfig.withValue(AkkaConfig.AkkaClusterSeedNodes, ConfigValueFactory.fromIterable(seedsAddresses.asJava))

        case None =>
          baseConfig
      }
    } else {
      baseConfig
    }
  }

  /**
   * Merges the Convergence Server Roles from the supplied config with those
   * set in the environment variable. Preference is given to what was
   * explicitly set in the config file.
   * @param baseConfig The original Config object.
   * @return The merged config object.
   */
  private[this] def mergeServerRoles(baseConfig: Config): Config = {
    val rolesInConfig = baseConfig.getStringList("akka.cluster.roles").asScala.toList
    if (rolesInConfig.isEmpty) {
      Option(System.getenv().get(Environment.ServerRoles)) match {
        case Some(rolesEnv) =>
          val roles = rolesEnv.split(",").toList map (_.trim)
          val updated = baseConfig.withValue("akka.cluster.roles", ConfigValueFactory.fromIterable(roles.asJava))
          updated
        case None =>
          baseConfig
      }
    } else {
      baseConfig
    }
  }

  /**
   * A helper method to validate that at least one Server Role is set in the config.
   * @param config The config to check.
   * @return Success if at least one role is set, Failure otherwise.
   */
  private[this] def validateRoles(config: Config): Try[Unit] = {
    val roles = config.getStringList(ConvergenceServer.AkkaConfig.AkkaClusterRoles).asScala.toList
    if (roles.isEmpty) {
      Failure(
        new IllegalStateException("No cluster roles were defined. " +
          s"Cluster roles must be defined in either the baseConfig '${ConvergenceServer.AkkaConfig.AkkaClusterRoles}s', " +
          s"or the environment variable '${ConvergenceServer.Environment.ServerRoles}'"))

    } else {
      Success(())
    }
  }

  /**
   * A helper method to validate that at least one seed node is set in the config.
   * @param config The config to check.
   * @return Success if at least one seed node is set, Failure otherwise.
   */
  private[this] def validateSeedNodes(config: Config): Try[Unit] = {
    val configuredSeeds = config.getAnyRefList(ConvergenceServer.AkkaConfig.AkkaClusterSeedNodes).asScala.toList
    if (configuredSeeds.isEmpty) {
      Failure(new IllegalStateException("No akka cluster seed nodes specified." +
        s"seed nodes must be specified in the akka baseConfig '${ConvergenceServer.AkkaConfig.AkkaClusterSeedNodes}' " +
        s"or the environment variable '${ConvergenceServer.Environment.ClusterSeedNodes}"))
    } else {
      Success(())
    }
  }

  /**
   * A helper method too re-initialize log4j using the specified config file.
   * The config file can either be specified as an Environment variable or a
   * command line argument. Preferences is given to the command line argument.
   * @param commandLineArg The potentially supplied command line argument.
   * @return Success if either no options were supplied, or if they were
   *         successfully applied; Failure otherwise.
   */
  def configureLogging(commandLineArg: Option[String] = None): Try[Unit] = {
    // Check for the environment baseConfig.
    val env: Option[String] = Option(System.getenv().get(Environment.Log4jConfigFile))

    // Take one or the other
    val config: Option[String] = (commandLineArg orElse env) ensuring (_ => (commandLineArg, env).zipped.isEmpty)

    Try {
      config.foreach { path =>
        info(s"${Environment.Log4jConfigFile} is set. Attempting to load logging baseConfig from: $path")
        val context = LogManager.getContext(false).asInstanceOf[org.apache.logging.log4j.core.LoggerContext]
        val file = new File(path)
        if (file.canRead) {
          info(s"Config file exists. Loading")
          // this will force a reconfiguration
          context.setConfigLocation(file.toURI)
        } else {
          warn(s"Log4j baseConfig file '$path' does not exist. Ignoring.")
        }
      }
    }
  }
}

/**
 * This is the main ConvergenceServer class. It is responsible for starting
 * up all services including the Akka Actor System.
 *
 * @param config The configuration to use for the server.
 */
class ConvergenceServer(private[this] val config: Config) extends Logging {

  import ConvergenceServer.Roles._

  private[this] var system: Option[ActorSystem] = None
  private[this] var cluster: Option[Cluster] = None
  private[this] var backend: Option[BackendNode] = None
  private[this] var orientDb: Option[OrientDB] = None
  private[this] var rest: Option[ConvergenceRestApi] = None
  private[this] var realtime: Option[ConvergenceRealtimeApi] = None

  /**
   * Starts the Convergence Server and returns itself, supporting
   * a fluent API.
   *
   * @return This instance of the ConvergenceServer
   */
  def start(): ConvergenceServer = {
    info(s"Convergence Server (${BuildInfo.version}) starting up...")

    val system = ActorSystem(ConvergenceServer.ActorSystemName, config)
    this.system = Some(system)

    val cluster = Cluster(system)
    this.cluster = Some(cluster)

    system.actorOf(Props(new AkkaClusterDebugListener(cluster)), name = "clusterListener")

    val roles = config.getStringList(ConvergenceServer.AkkaConfig.AkkaClusterRoles).asScala.toSet
    info(s"Convergence Server Roles: ${roles.mkString(", ")}")

    this.processBackendRole(roles, system)
    this.activateShardProxies(roles, system)
    this.processRestApiRole(roles, system)
    this.processRealtimeApiRole(roles, system)

    this
  }

  /**
   * Stops the ConvergenceServer.
   */
  def stop(): Unit = {
    logger.info(s"Stopping the Convergence Server")

    this.backend.foreach(backend => backend.stop())
    this.rest.foreach(rest => rest.stop())
    this.realtime.foreach(realtime => realtime.stop())
    this.orientDb.foreach(db => db.close())

    logger.info(s"Leaving the cluster.")
    cluster.foreach(c => c.leave(c.selfAddress))

    system foreach { s =>
      logger.info(s"Terminating actor system.")
      s.terminate()
      Await.result(s.whenTerminated, FiniteDuration(10, TimeUnit.SECONDS))
      logger.info(s"Actor system terminated.")
    }
  }

  /**
   * A helper method that will bootstrap the backend node.
   *
   * @param roles The roles this server is configured with.
   * @param system The Akka [[ActorSystem]] this server is using.
   */
  private[this] def processBackendRole(roles: Set[String], system: ActorSystem): Unit = {
    if (roles.contains(Backend)) {
      val initializer = new ConvergenceInitializer(config, system.dispatcher)
      val persistenceConfig = config.getConfig("convergence.persistence")
      val dbServerConfig = persistenceConfig.getConfig("server")
      val convergenceDbConfig = persistenceConfig.getConfig("convergence-database")

      // TODO this only works is there is one ConvergenceServer with the
      //  backend role. This is fine for development, which is the only place
      //  this should exist, but it would be nice to do this elsewhere.
      if (convergenceDbConfig.hasPath("auto-install")) {
        if (convergenceDbConfig.getBoolean("auto-install.enabled")) {
          initializer.bootstrapConvergenceDatabase(config) recover {
            case cause: Exception =>
              logger.error("Could not bootstrap database", cause)
              System.exit(0)
          }
        }
      }

      val baseUri = dbServerConfig.getString("uri")
      orientDb = Some(new OrientDB(baseUri, OrientDBConfig.defaultConfig()))

      // TODO make the pool size configurable
      val convergenceDatabase = convergenceDbConfig.getString("database")
      val username = convergenceDbConfig.getString("username")
      val password = convergenceDbConfig.getString("password")

      val dbProvider = new PooledDatabaseProvider(baseUri, convergenceDatabase, username, password)
      dbProvider.connect().get

      if (config.hasPath("convergence.default-server-admin")) {
        initializer.autoConfigureServerAdmin(dbProvider, config.getConfig("convergence.default-server-admin"))
      }

      val domainStore = new DomainStore(dbProvider)
      system.actorOf(
        DomainPersistenceManagerActor.props(baseUri, domainStore),
        DomainPersistenceManagerActor.RelativePath)

      info("Role 'backend' configured on node, starting up backend.")
      val backend = new BackendNode(system, dbProvider)
      backend.start()
      this.backend = Some(backend)
    }
  }

  /**
   * A helper method that start the Akka Cluster Shard Proxies if needed.
   *
   * @param roles The roles this server is configured with.
   * @param system The Akka [[ActorSystem]] this server is using.
   */
  private[this] def activateShardProxies(roles: Set[String], system: ActorSystem): Unit = {
    if (roles.contains(Backend) && (roles.contains(RestApi) || roles.contains(RealtimeApi))) {
      // We only need to set up these proxies if we are NOT already on a
      // backend node, because the backend node creates the real shard
      // regions.  We only need the proxies if we are on a rest or realtime
      // node  that isn't also a backend node.

      // TODO Re-factor This to some setting in the config
      val shards = 100
      DomainActorSharding.startProxy(system, shards)
      RealtimeModelSharding.startProxy(system, shards)
      ChatSharding.startProxy(system, shards)
      RestDomainActorSharding.startProxy(system, shards)
      ActivityActorSharding.startProxy(system, shards)
    }
  }

  /**
   * A helper method that will bootstrap the Rest API.
   *
   * @param roles The roles this server is configured with.
   * @param system The Akka [[ActorSystem]] this server is using.
   */
  private[this] def processRestApiRole(roles: Set[String], system: ActorSystem): Unit = {
    if (roles.contains(RestApi)) {
      info("Role 'restApi' configured on node, activating rest api.")
      val host = config.getString("convergence.rest.host")
      val port = config.getInt("convergence.rest.port")
      val restFrontEnd = new ConvergenceRestApi(system, host, port)
      restFrontEnd.start()
      this.rest = Some(restFrontEnd)
    }
  }

  /**
   * A helper method that will bootstrap the Realtime Api.
   *
   * @param roles The roles this server is configured with.
   * @param system The Akka [[ActorSystem]] this server is using.
   */
  private[this] def processRealtimeApiRole(roles: Set[String], system: ActorSystem): Unit = {
    if (roles.contains(RealtimeApi)) {
      info("Role 'realtimeApi' configured on node, activating up realtime api.")
      val host = config.getString("convergence.realtime.host")
      val port = config.getInt("convergence.realtime.port")
      val realTimeFrontEnd = new ConvergenceRealtimeApi(system, host, port)
      realTimeFrontEnd.start()
      this.realtime = Some(realTimeFrontEnd)
    }
  }
}
