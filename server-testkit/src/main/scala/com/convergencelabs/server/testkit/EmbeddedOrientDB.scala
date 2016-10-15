package com.convergencelabs.server.testkit

import com.orientechnologies.orient.server.OServerMain
import com.orientechnologies.orient.server.config.OServerConfiguration
import com.orientechnologies.orient.server.config.OServerUserConfiguration
import grizzled.slf4j.Logging
import com.orientechnologies.orient.client.remote.OServerAdmin
import java.io.FileInputStream
import java.io.File

class EmbeddedOrientDB extends Logging {
  val server = OServerMain.create()
  val admin = new OServerAdmin("remote:localhost")

  def start(): Unit = {
    logger.info("Starting up embedded OrientDB")
    val odbTarget = new File("target/orientdb")

    if (odbTarget.exists()) {
      FileUtils.deleteDirectory(odbTarget)
    }

    odbTarget.mkdirs()

    val configFile = getClass.getResourceAsStream("/orientdb-server-config.xml")
    server.startup(configFile)
    server.activate()
    admin.connect("root", "password")
    logger.info("OrientDB started")
  }

  def stop(): Unit = {
    server.shutdown()
  }

  def createDatabase(dbName: String): Unit = {
    admin.createDatabase(dbName, "document", "plocal");
  }
}
