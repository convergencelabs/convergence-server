package com.convergencelabs.server.testkit

import java.io.File

import com.orientechnologies.orient.client.remote.OServerAdmin
import com.orientechnologies.orient.server.OServerMain

import grizzled.slf4j.Logging

class EmbeddedOrientDB extends Logging {
  val server = OServerMain.create()
  val admin = new OServerAdmin("remote:localhost")

  val persistent = java.lang.Boolean.getBoolean("convergence.test-server.persistent")
  val odbTarget = new File("target/orientdb")

  def start(): Unit = {
    logger.info("Starting up EmbeddedOrientDB")
    logger.info("OrientDB Path: " + odbTarget.getAbsolutePath)

    if (!persistent && odbTarget.exists()) {
      FileUtils.deleteDirectory(odbTarget)
    }

    if (!odbTarget.exists()) {
      odbTarget.mkdirs()
    }

    val configFile = getClass.getResourceAsStream("/orientdb-server-config.xml")
    server.startup(configFile)
    server.activate()
    
    logger.info("OrientDB Database Path: " + server.getDatabaseDirectory)
    admin.connect("root", "password")
    
    logger.info("EmbeddedOrientDB started")
  }

  def stop(): Unit = {
    server.shutdown()

    if (!persistent && odbTarget.exists()) {
      FileUtils.deleteDirectory(odbTarget)
    }
  }

  def createDatabase(dbName: String): Unit = {
    admin.createDatabase(dbName, "document", "plocal");
  }
}
