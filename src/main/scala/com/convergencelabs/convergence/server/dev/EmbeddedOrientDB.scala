/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.dev

import java.io.File

import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.server.OServer
import com.orientechnologies.orient.server.config.{OServerConfigurationManager, OServerEntryConfiguration}
import grizzled.slf4j.Logging

/**
 * This class starts up and embedded instance of OrientDB as a "in process" database.
 *
 * @param databasesPath The path of the desired OrientDB "databases" directory.
 * @param persistent    Whether data  should be kept between runs.
 */
private[dev] final class EmbeddedOrientDB(databasesPath: String,
                                          persistent: Boolean)
  extends Logging {

  OLogManager.instance().setWarnEnabled(false)
  OLogManager.instance().setConsoleLevel("SEVERE")

  private[this] val server = new OServer(false)
  private[this] val databasesDir = new File(databasesPath)

  /**
   * Starts up the embedded OrientDB instance.
   */
  def start(): Unit = {
    logger.info("Starting up Embedded OrientDB")
    if (!persistent && databasesDir.exists()) {
      logger.info("Removing old data, because the server is set to non-persistent.")
      deleteDirectory(databasesDir)
    }

    if (!databasesDir.exists()) {
      databasesDir.mkdirs()
    }

    // Set OrientDB home to current directory
    val orientDbHome = new File("").getAbsolutePath
    System.setProperty("ORIENTDB_HOME", orientDbHome)

    val configFile = new File("./src/orientdb/config/orientdb-server-config.xml")
    val serverCfg = new OServerConfigurationManager(configFile)
    val config = serverCfg.getConfiguration
    val properties = config.properties.toList filter (_.name != "server.database.path")
    val withData = properties ++ List(new OServerEntryConfiguration("server.database.path", databasesDir.getAbsolutePath))
    config.properties = withData.toArray
    server.startup(config)
    server.activate()

    logger.info(s"Embedded OrientDB started at path: ${server.getDatabaseDirectory}")
  }

  /**
   * Shuts down the embedded OrientDB instance.
   */
  def stop(): Unit = {
    logger.info(s"Stopping the Embedded Orient DB.")
    server.shutdown()
    logger.info(s"Embedded Orient DB stopped.")
  }

  private[this] def deleteDirectory(path: File): Boolean = {
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
