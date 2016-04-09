package com.convergencelabs.server.datastore

import scala.util.Try
import akka.actor.Status
import scala.util.Success
import akka.actor.Status
import scala.util.Failure

object ReplyUtil {
  
  implicit class ReplyTry[T](val t: Try[T]) extends AnyVal {
    def mapReply(f: PartialFunction[T, Any]): Any = {
      t match {
        case Failure(cause) => Status.Failure(cause)
        case Success(x) => f(x)
      }
    }
  }
}