package com.convergencelabs.server.testkit

import java.io.File

import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.server.OServer
import com.orientechnologies.orient.server.config.OServerConfigurationManager
import com.orientechnologies.orient.server.config.OServerEntryConfiguration

import grizzled.slf4j.Logging

class EmbeddedOrientDB(dataPath: String, persistent: Boolean) extends Logging {
  OLogManager.instance().setWarnEnabled(false)
  OLogManager.instance().setConsoleLevel("SEVERE")
  
  val server = new OServer()

  val odbTarget = new File(dataPath)

  def start(): Unit = {
    logger.info("Starting up Embedded OrientDB")
    if (!persistent && odbTarget.exists()) {
      logger.info("Removing old data, because the server is set to non-persistent.")
      deleteDirectory(odbTarget)
    }

    if (!odbTarget.exists()) {
      odbTarget.mkdirs()
    }
    
    //Set OrientDB home to current directory
    val orientDbHome = new File("").getAbsolutePath
    System.setProperty("ORIENTDB_HOME", orientDbHome)

    val configFile = new File("./src/orientdb/config/orientdb-server-config.xml")
    val serverCfg = new OServerConfigurationManager(configFile)
    val config = serverCfg.getConfiguration
    val properties = config.properties.toList filter { _.name != "server.database.path" }
    val withData = properties ++ List(new OServerEntryConfiguration("server.database.path", odbTarget.getAbsolutePath))
    config.properties = withData.toArray
    server.startup(config)
    server.activate()
    
    logger.info(s"Embedded OrientDB started at path: ${server.getDatabaseDirectory}")
  }

  def stop(): Unit = {
    logger.info(s"Stopping the Embedded Orient DB.")
    server.shutdown()
    logger.info(s"Embedded Orient DB stopped.")
  }

  def deleteDirectory(path: File): Boolean = {
    if (path.exists()) {
      val files = path.listFiles().toList
      files.foreach { file =>
        if (file.isDirectory) {
          deleteDirectory(file)
        } else {
          file.delete()
        }
      }
    }

    path.delete()
  }
}
