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

package com.convergencelabs.convergence.server.util

import java.io.File

import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.server.OServer
import com.orientechnologies.orient.server.config.OServerConfigurationManager
import com.orientechnologies.orient.server.config.OServerEntryConfiguration

import grizzled.slf4j.Logging

class EmbeddedTestingOrientDB(dataPath: String, persistent: Boolean) extends Logging {
  OLogManager.instance().setWarnEnabled(false)
  OLogManager.instance().setConsoleLevel("SEVERE")
  OLogManager.instance().setFileLevel("SEVERE")

  val server = new OServer(false)

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
    val serverCfg = new OServerConfigurationManager(configFile)
    val config = serverCfg.getConfiguration
    val properties = config.properties.toList filter { _.name != "server.database.path" }
    val withData = properties ++ List(new OServerEntryConfiguration("server.database.path", odbTarget.getAbsolutePath))
    config.properties = withData.toArray
    server.startup(config)
    server.activate()

    logger.info(s"OrientDB started at path: ${server.getDatabaseDirectory}")
  }

  def stop(): Unit = {
    logger.info(s"Stopping OrientDB")
    server.shutdown()
    server.waitForShutdown()
    server.getDatabases.close()
    logger.info(s"OrientDB Stopped")
    
    if (!persistent) {
      logger.info(s"Deleting OrientDB database directory: ${server.getDatabaseDirectory}")
      deleteDirectory(odbTarget)
    }
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
