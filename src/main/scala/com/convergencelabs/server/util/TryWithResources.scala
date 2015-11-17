package com.convergencelabs.server.util

import java.io._
import scala.util.Try
import scala.util.Success
import scala.util.control.NonFatal
import scala.util.Failure

class TryWithResource[A <: AutoCloseable](resource: A) {
  def tryWithResource[B](block: A => B): Try[B] = {
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
  def apply[A <: AutoCloseable, B](resource: A)(r: A => B): Try[B] =
    new TryWithResource(resource).tryWithResource(r)
}