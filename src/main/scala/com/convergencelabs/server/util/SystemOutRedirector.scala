/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

import org.slf4j.LoggerFactory
import org.slf4j.Logger
import java.io.PrintStream
import java.io.OutputStream;

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
