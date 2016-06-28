package com.convergencelabs.server.util.concurrent

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object FutureUtils {
  def tryToFuture[A](t: => Try[A]): Future[A] = {
    t match {
      case Success(s) => Future.successful(s)
      case Failure(fail) => Future.failed(fail)
    }
  }
}