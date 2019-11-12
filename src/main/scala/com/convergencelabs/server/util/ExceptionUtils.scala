/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

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
