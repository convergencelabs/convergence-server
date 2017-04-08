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

object TestServer {
  def main(args: Array[String]): Unit = {
    val server = new TestServer("/convergence-application.conf")
    server.start()
  }
}

class TestServer(configFile: String) extends Logging {

  val persistent = java.lang.Boolean.getBoolean("convergence.test-server.persistent")
  val odbTarget = new File("target/orientdb")

  val reader = new InputStreamReader(getClass.getResourceAsStream(configFile))
  val config = ConfigFactory.parseReader(reader)
  val server = new ConvergenceServerNode(config)
  val oriendDb = new EmbeddedOrientDB(odbTarget.getAbsolutePath, persistent)

  sys.ShutdownHookThread {
    println("exiting")
  }

  def start(): Unit = {
    logger.info("Test Server starting up")
    oriendDb.start()
    server.start()
    logger.info("Test Server started.")
  }

  def stop(): Unit = {
    server.stop()
    oriendDb.stop()
  }
}
