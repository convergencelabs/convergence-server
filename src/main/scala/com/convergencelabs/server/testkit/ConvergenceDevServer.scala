package com.convergencelabs.server.testkit

import java.io.{File, FileInputStream, InputStreamReader}

import com.convergencelabs.server.ConvergenceServer
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import grizzled.slf4j.Logging
import org.apache.logging.log4j.LogManager

import scala.collection.JavaConverters.seqAsJavaListConverter

object ConvergenceDevServer {
  def main(args: Array[String]): Unit = {
    val server = new ConvergenceDevServer()
    server.start()
  }
}

class ConvergenceDevServer() extends Logging {

  ConvergenceServer.configureLogging(Some("src/dev-server/log4j2.xml"))

  private[this] val ConfigFile = "src/dev-server/convergence-server.conf"

  private[this] val persistent = java.lang.Boolean.getBoolean("convergence.dev-server.persistent")
  private[this] val odbTarget = new File("target/orientdb/databases")

  private[this] val seed = new ConvergenceServer(
    createConfig(ConfigFile, 2551, List("seed")))
  private[this] val backend = new ConvergenceServer(
    createConfig(ConfigFile, 2552, List(ConvergenceServer.Roles.Backend)))
  private[this] val frontend = new ConvergenceServer(
    createConfig(ConfigFile, 2553, List(ConvergenceServer.Roles.RealtimeApi, ConvergenceServer.Roles.RestApi)))

  private[this] val orientDb = new EmbeddedOrientDB(odbTarget.getAbsolutePath, persistent)

  def start(): Unit = {
    logger.info("Convergence Development Server starting up...")

    orientDb.start()

    seed.start()
    backend.start()
    frontend.start()

    logger.info("Convergence Development Server started.")

    scala.sys.addShutdownHook {
      logger.info("Convergence Development Server JVM Shutdown Hook called.")
      this.stop()
    }

    var done = false
    do {
      val line = scala.io.StdIn.readLine()
      done = Option(line).isEmpty || line.trim() == "exit"
    } while (!done)

    sys.exit(0)
  }

  def stop(): Unit = {
    logger.info("Convergence Development Server shutting down...")
    seed.stop()
    backend.stop()
    frontend.stop()
    orientDb.stop()
    LogManager.shutdown()
  }

  def createConfig(configFile: String, port: Int, roles: List[String]): Config = {
    val reader = new InputStreamReader(new FileInputStream(configFile))
    val parsed = ConfigFactory.parseReader(reader)
      .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(port))
      .withValue("akka.cluster.roles", ConfigValueFactory.fromIterable(roles.asJava))
      
    ConfigFactory.load(parsed)
  }
}
