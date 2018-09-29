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
