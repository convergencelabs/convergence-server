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

import akka.actor.Address
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, Scheduler}
import akka.util.Timeout
import com.convergencelabs.convergence.server.ConvergenceServerActor.Message
import com.convergencelabs.convergence.server.util.SystemOutRedirector
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import grizzled.slf4j.Logging
import org.apache.logging.log4j.LogManager

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.jdk.CollectionConverters._
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
   * String constants for various Akka baseConfig keys that are used during
   * initialization.
   */
  object AkkaConfig {
    val AkkaClusterRoles = "akka.cluster.roles"
    val AkkaClusterSeedNodes = "akka.cluster.seed-nodes"
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
   * The name of the Akka ActorSystem.
   */
  val ActorSystemName: String = "Convergence"

  /**
   * The currently running instance of the ConvergenceServer.
   */
  private[this] var system: Option[ActorSystem[Message]] = None

  /**
   * The main entry point of the ConvergenceServer.
   *
   * @param args Command line arguments.
   * @see ConvergenceServerCLIConf
   */
  def main(args: Array[String]): Unit = {
    val options = ConvergenceServerCLIConf(args.toIndexedSeq)

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
      val system: ActorSystem[Message] = ActorSystem(ConvergenceServerActor(), ActorSystemName, config)
      this.system = Some(system)

      implicit val t: Timeout = Timeout(Duration.fromNanos(
        system.settings.config.getDuration("convergence.server-startup-timeout").toNanos))

      implicit val s: Scheduler = system.scheduler
      implicit val ec: ExecutionContext = system.executionContext
      system
        .ask[ConvergenceServerActor.StartResponse](ConvergenceServerActor.StartRequest)
        .map(_.response match {
          case Left(_) =>
            error("There was a failure on server start up. Exiting.")
            this.terminateAndExitOnError(system)
          case Right(_) =>
        })
        .recover { _ =>
          error("The server did not start up in time. Exiting.")
          system.terminate()
          System.exit(1)
        }
    }).recover {
      case cause: Throwable =>
        logger.error("Could not start Convergence Server", cause)
    }
  }

  /**
   * Helper method that will shut down the server, if it was started.
   */
  private[this] def stop(): Unit = {
    this.system.foreach(system => {
      implicit val t: Timeout = Timeout(Duration.fromNanos(
        system.settings.config.getDuration("convergence.server-shutdown-timeout").toNanos))

      implicit val sys: Scheduler = system.scheduler
      implicit val ec: ExecutionContext = system.executionContext
      system
        .ask[ConvergenceServerActor.StopResponse](ConvergenceServerActor.StopRequest)
        .map { _ =>
          terminate(system)
          System.exit(0)
        }
        .recover { _ =>
          error("The server did not stop up in time. Exiting.")
          terminateAndExitOnError(system)
        }
    })
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
      if (!configFile.canRead()) {
        Failure(new IllegalArgumentException(s"Can not read config file: ${configFile.getAbsolutePath}."))
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
                (host, 25520)
              case _ =>
                throw new IllegalArgumentException(s"Invalid seed node environment variable $seedNodesEnv")
            }
          })
          val seedsAddresses = seedNodes.map { seed =>
            Address("akka", ActorSystemName, seed._1, seed._2).toString
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
    val rolesInConfig = baseConfig.getStringList(AkkaConfig.AkkaClusterRoles).asScala.toList
    if (rolesInConfig.isEmpty) {
      logger.info(s"No roles specified in the config file. Looking for the '${Environment.ConvergenceServerRoles}' environment variable.")
      Option(System.getenv().get(Environment.ConvergenceServerRoles)) match {
        case Some(rolesEnv) if rolesEnv.trim.length > 0 =>
          val roles = rolesEnv.split(",").toList.map(_.trim).filter(_.nonEmpty)
          val updated = baseConfig.withValue(AkkaConfig.AkkaClusterRoles, ConfigValueFactory.fromIterable(roles.asJava))
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
    val roles = config.getStringList(AkkaConfig.AkkaClusterRoles).asScala.toList
    if (roles.isEmpty) {
      Failure(
        new IllegalStateException("No cluster roles were defined. " +
          s"Cluster roles must be defined in either the config '${AkkaConfig.AkkaClusterRoles}', " +
          s"or the environment variable '${Environment.ConvergenceServerRoles}'"))

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
    val configuredSeeds = config.getAnyRefList(AkkaConfig.AkkaClusterSeedNodes).asScala.toList
    if (configuredSeeds.isEmpty) {
      Failure(new IllegalStateException("No akka cluster seed nodes specified." +
        s"seed nodes must be specified in the akka baseConfig '${AkkaConfig.AkkaClusterSeedNodes}' " +
        s"or the environment variable '${Environment.ConvergenceClusterSeeds}"))
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
    val config: Option[String] = logFile orElse env ensuring (_ => (logFile zip env).isEmpty)

    Try {
      config.foreach { path =>
        info(s"${Environment.ConvergenceLog4jConfigFile} is set: $path")
        val file = new File(path)
        if (file.canRead) {
          info(s"Log4J config file exists; reloading logging config with the specified configuration.")
          val context = LogManager.getContext(false).asInstanceOf[org.apache.logging.log4j.core.LoggerContext]
          // this will force a reconfiguration
          context.setConfigLocation(file.toURI)
        } else {
          warn(s"Log4j baseConfig file '$path' does not exist. Ignoring.")
        }
      }
    }
  }

  /**
   * Helper method to wait on the server to shut down.
   *
   * @param system The ActorSystem to shutdown.
   */
  private[this] def terminate(system: ActorSystem[Message]): Unit = {
    logger.info(s"Terminating ActorSystem...")

    system.terminate()

    val timeout = Duration.fromNanos(system.settings.config.getDuration("convergence.system-termination-timeout").toNanos)

    Try(
      Await.result(system.whenTerminated, timeout)
    )

    logger.info(s"ActorSystem terminated")

    LogManager.shutdown()
  }

  /**
   * A helper method to exit uncleanly.
   *
   * @param system The system to terminate.
   */
  private[this] def terminateAndExitOnError(system: ActorSystem[Message]): Unit = {
    terminate(system)
    System.exit(1)
  }
}
