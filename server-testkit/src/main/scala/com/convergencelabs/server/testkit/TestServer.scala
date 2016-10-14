package com.convergencelabs.server.testkit

import java.io.File

import com.convergencelabs.server.ConvergenceServerNode
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.typesafe.config.ConfigFactory

import grizzled.slf4j.Logging

object TestServer {
  def main(args: Array[String]): Unit = {
    val server = new TestServer("test-server/convergence-application.conf")
    server.start()
  }
}

class TestServer(configFile: String) extends Logging {
  // Override the configuration of the port
  val config = ConfigFactory.parseFile(new File(configFile))
  val server = new ConvergenceServerNode(config)
  val oriendDb = new EmbeddedOrientDB();

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
