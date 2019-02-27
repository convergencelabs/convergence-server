package com.convergencelabs.server.testkit

import java.io.File
import java.io.InputStreamReader

import scala.collection.JavaConverters.seqAsJavaListConverter

import org.apache.logging.log4j.LogManager

import com.convergencelabs.server.ConvergenceServerNode
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

import grizzled.slf4j.Logging

object ConvergenceDevServer {
  def main(args: Array[String]): Unit = {
    val server = new ConvergenceDevServer()
    server.start()
  }
}

class ConvergenceDevServer() extends Logging {

  ConvergenceServerNode.configureLogging()

  val persistent = java.lang.Boolean.getBoolean("convergence.dev-server.persistent")
  val odbTarget = new File("target/orientdb/databases")

  val seed = new ConvergenceServerNode(
    createConfig("/convergence-server.conf", 2551, List("seed")))
  val backend = new ConvergenceServerNode(
    createConfig("/convergence-server.conf", 2552, List(ConvergenceServerNode.Roles.Backend)))
  val frontend = new ConvergenceServerNode(
    createConfig("/convergence-server.conf", 2553, List(ConvergenceServerNode.Roles.RealtimeApi, ConvergenceServerNode.Roles.RestApi)))

  val oriendDb = new EmbeddedOrientDB(odbTarget.getAbsolutePath, persistent)

  def start(): Unit = {
    logger.info("Test server starting up")

    oriendDb.start()

    seed.start()
    backend.start()
    frontend.start()

    logger.info("Test server started.")
    
    scala.sys.addShutdownHook {
      logger.info("Test server JVM Shutdown Hook called.")
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
    logger.info("Test server shutting down.")
    seed.stop()
    backend.stop()
    frontend.stop()
    oriendDb.stop()
    LogManager.shutdown();
  }

  def createConfig(configFile: String, port: Int, roles: List[String]): Config = {
    val reader = new InputStreamReader(getClass.getResourceAsStream(configFile))
    val parsed = ConfigFactory.parseReader(reader)
      .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(port))
      .withValue("akka.cluster.roles", ConfigValueFactory.fromIterable(roles.asJava))
      
    ConfigFactory.load(parsed)
  }
}
