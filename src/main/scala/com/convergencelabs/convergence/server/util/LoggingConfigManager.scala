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

import grizzled.slf4j.Logging
import org.apache.logging.log4j.LogManager

import scala.util.Try

object LoggingConfigManager extends Logging {
  /**
   * A helper method too re-initialize log4j using the specified config file.
   * The config file can either be specified as an Environment variable or a
   * method argument. Preferences is given to the command line argument.
   *
   * @param logFile The log file to configure logging with
   * @return Success if either no options were supplied, or if they were
   *         successfully applied; Failure otherwise.
   */
  def configureLogging(logFile: String): Try[Unit] = {
    Try {
        val file = new File(logFile)
        if (file.canRead) {
          info(s"Log4J config file exists; reloading logging config with the specified configuration: $logFile")
          val context = LogManager.getContext(false).asInstanceOf[org.apache.logging.log4j.core.LoggerContext]
          // this will force a reconfiguration
          context.setConfigLocation(file.toURI)
        } else {
          warn(s"Log4j baseConfig file '$logFile' does not exist. Ignoring.")
        }
    }
  }
}
