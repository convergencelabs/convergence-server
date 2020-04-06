/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Address, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.realtime.ConvergenceRealtimeApi
import com.convergencelabs.convergence.server.api.rest.ConvergenceRestApi
import com.convergencelabs.convergence.server.datastore.convergence._
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.convergence.server.db.{ConvergenceDatabaseInitializerActor, PooledDatabaseProvider}
import com.convergencelabs.convergence.server.domain.DomainActorSharding
import com.convergencelabs.convergence.server.domain.activity.ActivityActorSharding
import com.convergencelabs.convergence.server.domain.chat.ChatSharding
import com.convergencelabs.convergence.server.domain.model.RealtimeModelSharding
import com.convergencelabs.convergence.server.domain.rest.RestDomainActorSharding
import com.convergencelabs.convergence.server.util.SystemOutRedirector
import com.orientechnologies.orient.core.db.{OrientDB, OrientDBConfig}
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions, ConfigValueFactory}
import grizzled.slf4j.Logging
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters.{asScalaBufferConverter, seqAsJavaListConverter}
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, FiniteDuration}
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
    val ConvergenceServerRoles = "CONVERGENCE_SERVER_ROLES"
    val ConvergenceClusterSeeds = "CONVERGENCE_CLUSTER_SEEDS"
    val ConvergenceLog4jConfigFile = "CONVERGENCE_LOG4J_CONFIG_FILE"
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
  val ActorSystemName: String = "Convergence"

  /**
   * The currently running instance of the ConvergenceServer.
   */
  private var server: Option[ConvergenceServer] = None

  /**
   * The main entry point of the ConvergenceServer.
   *
   * @param args Command line arguments.
   * @see ConvergenceServerCLIConf
   */
  def main(args: Array[String]): Unit = {
    val options = ConvergenceServerCLIConf(args)

    SystemOutRedirector.setOutAndErrToLog()

    scala.sys.addShutdownHook {
      logger.info("JVM Shutdown Hook invoked, stopping services")
      this.stop()
    }

    (for {
      _ <- configureLogging()
      configFile <- getConfigFile(options)
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
   *
   * @param options The command line arguments supplied to the main method.
   * @return The File reference to the baseConfig file if it exists.
   */
  private[this] def getConfigFile(options: ConvergenceServerCLIConf): Try[File] = {
    Try {
      new File(options.config.toOption.get.trim)
    } flatMap { configFile =>
      if (!configFile.exists()) {
        Failure(new IllegalArgumentException(s"Config file not found: ${configFile.getAbsolutePath}."))
      } else {
        info(s"Using config file: ${configFile.getAbsolutePath}")
        Success(configFile)
      }
    }
  }

  /**
   * A helper method that will integrate the Akka / Lightbend baseConfig file with
   * command line arguments and environment variables.
   *
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
   *
   * @param baseConfig The original Config object.
   * @return The merged Config object.
   */
  private[this] def mergeSeedNodes(baseConfig: Config): Config = {
    val configuredSeeds = baseConfig.getAnyRefList(AkkaConfig.AkkaClusterSeedNodes).asScala.toList
    if (configuredSeeds.isEmpty) {
      logger.info(s"No seed nodes specified in the config file. Looking for the '${Environment.ConvergenceClusterSeeds}' environment variable.")
      Option(System.getenv().get(Environment.ConvergenceClusterSeeds)) match {
        case Some(seedNodesEnv) =>
          logger.debug(s"Found ${Environment.ConvergenceClusterSeeds}: '$seedNodesEnv'")
          // Comma separated list of "host[:port]"
          // Could look like this: "host1:port1,host2:port2,host3"
          val seedNodes = seedNodesEnv.split(",").toList.map(entry => {
            entry.split(":").toList match {
              case host :: portString :: Nil =>
                val port = Try(Integer.parseInt(portString)).getOrElse {
                  throw new IllegalArgumentException(s"Invalid seed node configuration, invalid port number: $portString")
                }

                (host.trim, port)
              case host :: Nil =>
                (host, 2551)
              case _ =>
                throw new IllegalArgumentException(s"Invalid seed node environment variable $seedNodesEnv")
            }
          })
          val seedsAddresses = seedNodes.map { seed =>
            Address("akka.tcp", ConvergenceServer.ActorSystemName, seed._1, seed._2).toString
          }
          logger.debug(s"Setting cluster seeds: [${seedsAddresses.mkString(", ")}]")
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
   *
   * @param baseConfig The original Config object.
   * @return The merged config object.
   */
  private[this] def mergeServerRoles(baseConfig: Config): Config = {
    val rolesInConfig = baseConfig.getStringList("akka.cluster.roles").asScala.toList
    if (rolesInConfig.isEmpty) {
      Option(System.getenv().get(Environment.ConvergenceServerRoles)) match {
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
   *
   * @param config The config to check.
   * @return Success if at least one role is set, Failure otherwise.
   */
  private[this] def validateRoles(config: Config): Try[Unit] = {
    val roles = config.getStringList(ConvergenceServer.AkkaConfig.AkkaClusterRoles).asScala.toList
    if (roles.isEmpty) {
      Failure(
        new IllegalStateException("No cluster roles were defined. " +
          s"Cluster roles must be defined in either the config '${ConvergenceServer.AkkaConfig.AkkaClusterRoles}', " +
          s"or the environment variable '${ConvergenceServer.Environment.ConvergenceServerRoles}'"))

    } else {
      Success(())
    }
  }

  /**
   * A helper method to validate that at least one seed node is set in the config.
   *
   * @param config The config to check.
   * @return Success if at least one seed node is set, Failure otherwise.
   */
  private[this] def validateSeedNodes(config: Config): Try[Unit] = {
    val configuredSeeds = config.getAnyRefList(ConvergenceServer.AkkaConfig.AkkaClusterSeedNodes).asScala.toList
    if (configuredSeeds.isEmpty) {
      Failure(new IllegalStateException("No akka cluster seed nodes specified." +
        s"seed nodes must be specified in the akka baseConfig '${ConvergenceServer.AkkaConfig.AkkaClusterSeedNodes}' " +
        s"or the environment variable '${ConvergenceServer.Environment.ConvergenceClusterSeeds}"))
    } else {
      Success(())
    }
  }

  /**
   * A helper method too re-initialize log4j using the specified config file.
   * The config file can either be specified as an Environment variable or a
   * method argument. Preferences is given to the command line argument.
   *
   * @param logFile The optionally supplied log4j file path.
   * @return Success if either no options were supplied, or if they were
   *         successfully applied; Failure otherwise.
   */
  def configureLogging(logFile: Option[String] = None): Try[Unit] = {
    // Check for the environment baseConfig.
    val env: Option[String] = Option(System.getenv().get(Environment.ConvergenceLog4jConfigFile))

    // Take one or the other
    val config: Option[String] = (logFile orElse env) ensuring (_ => (logFile, env).zipped.isEmpty)

    Try {
      config.foreach { path =>
        info(s"${Environment.ConvergenceLog4jConfigFile} is set. Attempting to load logging baseConfig from: $path")
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
  private[this] var backend: Option[BackendServices] = None
  private[this] var orientDb: Option[OrientDB] = None
  private[this] var rest: Option[ConvergenceRestApi] = None
  private[this] var realtime: Option[ConvergenceRealtimeApi] = None
  private[this] var clusterListener: Option[ActorRef] = None

  /**
   * Starts the Convergence Server and returns itself, supporting
   * a fluent API.
   *
   * @return This instance of the ConvergenceServer
   */
  def start(): ConvergenceServer = {
    info(s"Convergence Server (${BuildInfo.version}) starting up...")

    debug(s"Rendering configuration: \n ${config.root().render(ConfigRenderOptions.concise())}")

    val system = ActorSystem(ConvergenceServer.ActorSystemName, config)
    this.system = Some(system)

    val cluster = Cluster(system)
    this.cluster = Some(cluster)

    clusterListener = Some(system.actorOf(Props(new AkkaClusterDebugListener(cluster)), name = "clusterListener"))

    val roles = config.getStringList(ConvergenceServer.AkkaConfig.AkkaClusterRoles).asScala.toSet
    info(s"Convergence Server Roles: ${roles.mkString(", ")}")

    this.processBackendRole(roles, system)
    this.activateShardProxies(roles, system)
    this.processRestApiRole(roles, system)
    this.processRealtimeApiRole(roles, system)

    this
  }

  /**
   * Stops the Convergence Server.
   */
  def stop(): ConvergenceServer = {
    logger.info(s"Stopping the Convergence Server...")

    clusterListener.foreach(_ ! PoisonPill)

    this.backend.foreach(backend => backend.stop())
    this.rest.foreach(rest => rest.stop())
    this.realtime.foreach(realtime => realtime.stop())
    this.orientDb.foreach(db => db.close())

    logger.info(s"Leaving the cluster")
    cluster.foreach(c => c.leave(c.selfAddress))

    system foreach { s =>
      logger.info(s"Terminating actor system")
      s.terminate()
      Await.result(s.whenTerminated, FiniteDuration(10, TimeUnit.SECONDS))
      logger.info(s"ActorSystem terminated")
    }

    this
  }

  /**
   * A helper method that will bootstrap the backend node.
   *
   * @param roles  The roles this server is configured with.
   * @param system The Akka ActorSystem this server is using.
   */
  private[this] def processBackendRole(roles: Set[String], system: ActorSystem): Unit = {
    if (roles.contains(Backend)) {
      info("Role 'backend' detected, activating Backend Services...")

      val persistenceConfig = config.getConfig("convergence.persistence")
      val dbServerConfig = persistenceConfig.getConfig("server")
      val convergenceDbConfig = persistenceConfig.getConfig("convergence-database")

      system.actorOf(
        ClusterSingletonManager.props(
          singletonProps = ConvergenceDatabaseInitializerActor.props(),
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system).withRole("backend")),
        name = "ConvergenceDatabaseInitializer")

      val convergenceDatabaseInitializerActor = system.actorOf(
        ClusterSingletonProxy.props(
          singletonManagerPath = "/user/ConvergenceDatabaseInitializer",
          settings = ClusterSingletonProxySettings(system).withRole("backend")),
        name = "ConvergenceDatabaseInitializerProxy")

      val initTimeout = convergenceDbConfig.getDuration("initialization-timeout")
      val timeout = Timeout.durationToTimeout(Duration.fromNanos(initTimeout.toNanos))
      val f = convergenceDatabaseInitializerActor
        .ask(ConvergenceDatabaseInitializerActor.AssertInitialized())(timeout)
        .mapTo[Unit]

      info("Ensuring convergence database is initialized")
      Try(Await.result(f, timeout.duration)) match {
        case Success(_) =>

          val baseUri = dbServerConfig.getString("uri")
          orientDb = Some(new OrientDB(baseUri, OrientDBConfig.defaultConfig()))

          // TODO make the pool size configurable
          val convergenceDatabase = convergenceDbConfig.getString("database")
          val username = convergenceDbConfig.getString("username")
          val password = convergenceDbConfig.getString("password")

          val dbProvider = new PooledDatabaseProvider(baseUri, convergenceDatabase, username, password)
          dbProvider.connect().get


          val domainStore = new DomainStore(dbProvider)
          system.actorOf(
            DomainPersistenceManagerActor.props(baseUri, domainStore),
            DomainPersistenceManagerActor.RelativePath)

          val backend = new BackendServices(system, dbProvider)
          backend.start()
          this.backend = Some(backend)
        case Failure(cause) =>
          this.logger.error("Could not initialize the convergence database", cause)
          System.exit(1)
      }
    }
  }

  /**
   * A helper method that start the Akka Cluster Shard Proxies if needed.
   *
   * @param roles  The roles this server is configured with.
   * @param system The Akka ActorSystem this server is using.
   */
  private[this] def activateShardProxies(roles: Set[String], system: ActorSystem): Unit = {
    if (!roles.contains(Backend) && (roles.contains(RestApi) || roles.contains(RealtimeApi))) {
      // We only need to set up these proxies if we are NOT already on a
      // backend node, because the backend node creates the real shard
      // regions.  We only need the proxies if we are on a rest or realtime
      // node  that isn't also a backend node.

      val shardCount = system.settings.config.getInt("convergence.shard-count")
      DomainActorSharding.startProxy(system, shardCount)
      RealtimeModelSharding.startProxy(system, shardCount)
      ChatSharding.startProxy(system, shardCount)
      RestDomainActorSharding.startProxy(system, shardCount)
      ActivityActorSharding.startProxy(system, shardCount)
    }
  }

  /**
   * A helper method that will bootstrap the Rest API.
   *
   * @param roles  The roles this server is configured with.
   * @param system The Akka ActorSystem this server is using.
   */
  private[this] def processRestApiRole(roles: Set[String], system: ActorSystem): Unit = {
    if (roles.contains(RestApi)) {
      info("Role 'restApi' detected, activating REST API...")
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
   * @param roles  The roles this server is configured with.
   * @param system The Akka ActorSystem this server is using.
   */
  private[this] def processRealtimeApiRole(roles: Set[String], system: ActorSystem): Unit = {
    if (roles.contains(RealtimeApi)) {
      info("Role 'realtimeApi' detected, activating the Realtime API...")
      val host = config.getString("convergence.realtime.host")
      val port = config.getInt("convergence.realtime.port")
      val realTimeFrontEnd = new ConvergenceRealtimeApi(system, host, port)
      realTimeFrontEnd.start()
      this.realtime = Some(realTimeFrontEnd)
    }
  }
}
