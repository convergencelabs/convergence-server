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

package com.convergencelabs.convergence.server.dev

import java.io.{File, FileInputStream, InputStreamReader}
import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Scheduler}
import akka.util.Timeout
import com.convergencelabs.convergence.server.ConvergenceServer.ActorSystemName
import com.convergencelabs.convergence.server.ConvergenceServerActor.Message
import com.convergencelabs.convergence.server.util.concurrent.FutureUtils
import com.convergencelabs.convergence.server.{ConvergenceServer, ConvergenceServerActor, ServerClusterRoles}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import grizzled.slf4j.Logging
import org.apache.logging.log4j.LogManager

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.util.Success

/**
 * Provides the main method to start up an instance of the [[ConvergenceDevServer]].
 */
object ConvergenceDevServer {
  def main(args: Array[String]): Unit = {
    val server = new ConvergenceDevServer()
    server.start()
  }
}

/**
 * The [[ConvergenceDevServer]] provides an all-in-one instance of Convergence
 * configured in a clustered Actor System, composed of three actor systems. The
 * first actor system hosts a backend role, and the second hosts both the
 * realtimeApi, and restApi roles. A third actor system is stood up to act as
 * an Akka Cluster Seed Node. The [[ConvergenceDevServer]] also starts up an
 * embedded instance of OrientDB. Thus, a full instance of the Convergence
 * Server can be easily started. Having the backend services and the REST /
 * Realtime APIs hosted in different ActorSystems ensures that messages
 * are routed over the remoting protocol using the full clustering system
 * and serialization of messages occurs.
 *
 * OrientDB is run in an embedded mode.  However, consumers of the database will
 * still connect to it via the remote protocol even though it is resident in
 * the same JVM to more accurately represent hot the system  will be run
 * in production.
 *
 * The server will use the file at `src/dev-server/convergence-server.conf` for
 * configuration. And will use `src/dev-server/log4j2.xml` to configure log4j.
 *
 * This is intended to be used by developer to easily run an instance of
 * Convergence from their IDE when developing Convergence. It is not intended
 * to be used in any kind of production use case.
 */
class ConvergenceDevServer() extends Logging {

  ConvergenceServer.configureLogging(Some("src/dev-server/log4j2.xml"))

  private[this] val ConfigFile = "src/dev-server/convergence-server.conf"
  private[this] val persistent = java.lang.Boolean.getBoolean("convergence.dev-server.persistent")
  private[this] val odbTarget = new File("target/orientdb/databases")
  /**
   * Creates an Akka Actor System that will act as the cluster seed node.
   */
  private[this] val seed: ActorSystem[Unit] = ActorSystem(
    Behaviors.ignore[Unit],
    ActorSystemName,
    createConfig(ConfigFile, 25520, List("seed")))


  /**
   * This [[ConvergenceServer]] instance will run the Backend Services.
   */
  private[this] val backend: ActorSystem[Message] = ActorSystem(
    ConvergenceServerActor(),
    ActorSystemName,
    createConfig(ConfigFile, 25521, List(ServerClusterRoles.Backend)))

  /**
   * This [[ConvergenceServer]] instance  will run the Rest API and the
   * Realtime API.
   */
  private[this] val frontend: ActorSystem[Message] = ActorSystem(
    ConvergenceServerActor(),
    ActorSystemName,
    createConfig(ConfigFile, 25522, List(ServerClusterRoles.RealtimeApi, ServerClusterRoles.RestApi)))

  /**
   * An embedded instance of OrientDB that will be run in process in this JVM
   * along with the other services.
   */
  private[this] val orientDb = new EmbeddedOrientDB(odbTarget.getAbsolutePath, persistent)

  /**
   * Starts the [[ConvergenceDevServer]] and all services.
   */
  def start(): Unit = {
    logger.info("Convergence Development Server starting up...")

    orientDb.start()

    implicit val t: Timeout = Timeout(30, TimeUnit.SECONDS)
    implicit val sys: Scheduler = backend.scheduler
    implicit val ec: ExecutionContext = ExecutionContext.global

    import FutureUtils._

    val f1 = backend.ask[ConvergenceServerActor.StartResponse](ConvergenceServerActor.StartRequest)
    val f2 = frontend.ask[ConvergenceServerActor.StartResponse](ConvergenceServerActor.StartRequest)

    List(f1, f2) onComplete {
      case Success(_) :: Success(_) :: Nil =>
        logger.info("Convergence Development Server started")
      case _ =>
        logger.info("Convergence Development Server startup failed")
        System.exit(1)
    }

    scala.sys.addShutdownHook {
      logger.info("Convergence Development Server JVM Shutdown Hook called")
      this.stop()
    }

    var done = false
    do {
      val line = scala.io.StdIn.readLine()
      done = Option(line).isEmpty || line.trim() == "exit"
    } while (!done)

    System.exit(0)
  }

  /**
   * Stops all services in the [[ConvergenceDevServer]]
   */
  def stop(): Unit = {
    logger.info("Convergence Development Server shutting down...")
    seed.terminate()

    implicit val t: Timeout = Timeout(15, TimeUnit.SECONDS)
    implicit val sys: Scheduler = backend.scheduler
    implicit val ec: ExecutionContext = ExecutionContext.global

    import FutureUtils._

    val stop1 = backend.ask[ConvergenceServerActor.StopResponse](ConvergenceServerActor.StopRequest)
    val stop2 = frontend.ask[ConvergenceServerActor.StopResponse](ConvergenceServerActor.StopRequest)

    List(stop1, stop2) onComplete { _ =>
      backend.terminate()
      frontend.terminate()

      val terminate1 = backend.whenTerminated
      val terminate2 = frontend.whenTerminated

      List(terminate1, terminate2) onComplete { _ =>
        LogManager.shutdown()
        System.exit(0)
      }
    }

    orientDb.stop()
  }

  /**
   * A helper method to create a preprocessed [[Config]] to run one of the
   * Actor Systems. This will use the specified config file, but override
   * the roles and port.
   *
   * @param configFile The base config file to use.
   * @param port       The port to run the ActorSystem on.
   * @param roles      The Akka Cluster Roles to use.
   * @return A modified [[Config]] with the roles and port overridden.
   */
  private[this] def createConfig(configFile: String, port: Int, roles: List[String]): Config = {
    val reader = new InputStreamReader(new FileInputStream(configFile))
    val parsed = ConfigFactory.parseReader(reader)
      .withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(port))
      .withValue("akka.cluster.roles", ConfigValueFactory.fromIterable(roles.asJava))

    ConfigFactory.load(parsed)
  }
}
