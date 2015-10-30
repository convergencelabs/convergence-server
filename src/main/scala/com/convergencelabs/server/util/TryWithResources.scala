package com.convergencelabs.server.util

import java.io._
import scala.util.Try
import scala.util.Success
import scala.util.control.NonFatal
import scala.util.Failure

class TryWithResource[A <: AutoCloseable](resource: A) {
  def tryWithResource[B](block: A => B) = {
    var result: Try[B] = null
    var t: Throwable = null
    try {
      result = Success(block(resource))
    } catch {
      case NonFatal(e) => {
        t = e
        result = Failure(e)
      }
    } finally {
      if (resource != null) {
        if (t != null) {
          try {
            resource.close()
          } catch {
            case NonFatal(e) => t.addSuppressed(e)
          }
        } else {
          try {
            resource.close()
          } catch {
            case NonFatal(e) => result = Failure(e)
          }
        }
      }
    }
    result
  }
}

object TryWithResource {
  def apply[A <: AutoCloseable, T](resource: A)(r: A => T): Try[T] =
    new TryWithResource(resource).tryWithResource(r)
}