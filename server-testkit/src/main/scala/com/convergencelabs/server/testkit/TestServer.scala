package com.convergencelabs.server.testkit

import java.io.File

import com.convergencelabs.server.ConvergenceServerNode
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConversions._

import grizzled.slf4j.Logging
import java.io.InputStreamReader
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import org.apache.logging.log4j.LogManager

object TestServer {
  def main(args: Array[String]): Unit = {
    val server = new TestServer()
    server.start()
  }
}

class TestServer() extends Logging {

  ConvergenceServerNode.configureLogging()

  val persistent = java.lang.Boolean.getBoolean("convergence.test-server.persistent")
  val odbTarget = new File("target/orientdb/databases")

  val seed = new ConvergenceServerNode(
    createConfig("/convergence-server.conf", 2551, List("seed")))
  val backend = new ConvergenceServerNode(
    createConfig("/convergence-server.conf", 2552, List(ConvergenceServerNode.Roles.Backend)))
  val frontend = new ConvergenceServerNode(
    createConfig("/convergence-server.conf", 2553, List(ConvergenceServerNode.Roles.RealtimeFrontend, ConvergenceServerNode.Roles.RestFrontend)))

  val oriendDb = new EmbeddedOrientDB(odbTarget.getAbsolutePath, persistent)

  def start(): Unit = {
    logger.info("Test server starting up")

    oriendDb.start()

    seed.start()
    backend.start()
    frontend.start()

    logger.info("Test server started.")
    
    scala.sys.addShutdownHook {
      this.stop()
    }
    
    var done = false
    do {
      val line = scala.io.StdIn.readLine()
      done = line.trim() == "exit"
    } while (!done)
    
    sys.exit(0)
  }

  def stop(): Unit = {
    logger.info("Test server shutting down.")
    oriendDb.stop()
    seed.stop()
    backend.stop()
    frontend.stop()
    oriendDb.stop()
    LogManager.shutdown();
  }

  def createConfig(configFile: String, port: Int, roles: List[String]): Config = {
    val reader = new InputStreamReader(getClass.getResourceAsStream(configFile))
    ConfigFactory.parseReader(reader)
      .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(port))
      .withValue("akka.cluster.roles", ConfigValueFactory.fromIterable(roles))
      .resolve()
  }
}
