package com.convergencelabs.server.util

import java.io.File
import com.orientechnologies.orient.client.remote.OServerAdmin
import com.orientechnologies.orient.server.OServerMain
import com.orientechnologies.orient.server.config.OServerConfigurationManager
import com.orientechnologies.orient.server.config.OServerEntryConfiguration
import grizzled.slf4j.Logging

class EmbeddedOrientDB(dataPath: String, persistent: Boolean) extends Logging {
  val server = OServerMain.create(false)

  val odbTarget = new File(dataPath)

  def start(): Unit = {
    logger.info("Starting up embedded OrientDB")
    if (!persistent && odbTarget.exists()) {
      deleteDirectory(odbTarget)
    }

    if (!odbTarget.exists()) {
      odbTarget.mkdirs()
    }

    val configFile = getClass.getResourceAsStream("/orientdb-server-config.xml")
    val serverCfg = new OServerConfigurationManager(configFile);
    val config = serverCfg.getConfiguration()
    val properties = config.properties.toList filter { _.name != "server.database.path" }
    val withData = properties ++ List(new OServerEntryConfiguration("server.database.path", odbTarget.getAbsolutePath))
    config.properties = withData.toArray
    server.startup(config)
    server.activate()
    
    logger.info(s"OrientDB started at path: ${server.getDatabaseDirectory}")
  }

  def stop(): Unit = {
    server.shutdown()
    if (!persistent) {
      deleteDirectory(odbTarget)
    }
  }

  def deleteDirectory(path: File): Boolean = {
    if (path.exists()) {
      val files = path.listFiles().toList
      files.foreach { file =>
        if (file.isDirectory()) {
          deleteDirectory(file)
        } else {
          file.delete()
        }
      }
    }

    path.delete()
  }
}
