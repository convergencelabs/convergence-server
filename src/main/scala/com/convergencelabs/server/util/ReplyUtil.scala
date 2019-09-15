package com.convergencelabs.server.util

import scala.util.Try
import akka.actor.ActorRef
import scala.util.Success
import scala.util.Failure
import akka.actor.Status
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait ReplyUtil {

  def reply[T](result: Try[T], sender: ActorRef): Unit = {
    result match {
      case Success(x) =>
        sender ! x
      case Failure(cause) =>
        sender ! Status.Failure(cause)
    }
  }

  def reply[T](future: Future[T], sender: ActorRef)(implicit ec: ExecutionContext): Unit = {
    future onComplete {
      case Success(s) =>
        sender ! s
      case Failure(cause) =>
        sender ! Status.Failure(cause)
    }
  }
}
