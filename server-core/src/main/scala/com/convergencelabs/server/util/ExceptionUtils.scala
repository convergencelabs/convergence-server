package com.convergencelabs.server.util

import java.io.StringWriter
import java.io.PrintWriter

object ExceptionUtils {
  def stackTraceToString(e: Throwable): String = {
    val sr = new StringWriter()
    val w = new PrintWriter(sr)
    e.printStackTrace(w)
    val result = sr.getBuffer.toString()
    sr.close()
    w.close()
    result
  }
}
