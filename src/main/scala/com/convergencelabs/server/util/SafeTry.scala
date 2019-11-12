/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.util

import scala.util.Success
import scala.util.Failure
import scala.util.Try

object SafeTry {
  def apply[T](r: => T): Try[T] =
    try Success(r) catch {
      case e: Throwable => Failure(e)
    }
}
