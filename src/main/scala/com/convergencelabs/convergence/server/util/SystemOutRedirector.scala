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

import java.io.{OutputStream, PrintStream}

import org.slf4j.{Logger, LoggerFactory};

object SystemOutRedirector {

  def setOutAndErrToLog(): Unit = {
    setOutToLog();
    setErrToLog();
  }

  def setOutToLog(): Unit = {
    val out = new PrintStream(new LoggerStream(LoggerFactory.getLogger("SystemOut")));
    System.setOut(out);
  }

  def setErrToLog(): Unit = {
    val err = new PrintStream(new LoggerStream(LoggerFactory.getLogger("SystemErr")))
    System.setErr(err);
  }
}

private class LoggerStream(logger: Logger) extends OutputStream {

  override def write(b: Array[Byte]): Unit = {

    log(new String(b))
  }

  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    log(new String(b, off, len))
  }

  def write(b: Int): Unit = {
    log(String.valueOf(b.asInstanceOf[Char]))

  }

  def log(message: String): Unit = {
    if (!message.trim.isEmpty)
      logger.warn(message);
  }
}
