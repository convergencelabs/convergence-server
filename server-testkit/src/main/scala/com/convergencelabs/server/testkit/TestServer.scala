package com.convergencelabs.server.testkit

import java.io.File

import com.convergencelabs.server.ConvergenceServerNode
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.typesafe.config.ConfigFactory

import grizzled.slf4j.Logging
import java.io.InputStreamReader
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory

object TestServer {
  def main(args: Array[String]): Unit = {
    val server = new TestServer()
    server.start()
  }
}

class TestServer() extends Logging {

  val persistent = java.lang.Boolean.getBoolean("convergence.test-server.persistent")
  val odbTarget = new File("target/orientdb/databases")

  
  val seed = new ConvergenceServerNode(parseConfig("/convergence-application-seed.conf", 2551))
  val backend = new ConvergenceServerNode(parseConfig("/convergence-application-backend.conf", 2552))
  val frontend = new ConvergenceServerNode(parseConfig("/convergence-application-frontend.conf", 2553))
  
  val oriendDb = new EmbeddedOrientDB(odbTarget.getAbsolutePath, persistent)

  def start(): Unit = {
    logger.info("Test server starting up")
    oriendDb.start()
    seed.start()
    backend.start()
    frontend.start()
    
    logger.info("Test server started.")
    var line = scala.io.StdIn.readLine()
    while (line.trim() != "exit") {
      line = scala.io.StdIn.readLine()
    }
    this.stop()
    sys.exit()
  }

  def stop(): Unit = {
    logger.info("Test server shutting down.")
    seed.stop()
    backend.stop()
    frontend.stop()
    oriendDb.stop()
  }
  
  def parseConfig(configFile: String, port: Int): Config = {
    val reader = new InputStreamReader(getClass.getResourceAsStream(configFile))
    val config = ConfigFactory.parseReader(reader).withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(port))
    config
  }
}
