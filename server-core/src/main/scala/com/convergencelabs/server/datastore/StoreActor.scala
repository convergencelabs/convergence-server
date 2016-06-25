package com.convergencelabs.server.datastore

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Status
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

abstract class StoreActor private[datastore] extends Actor with ActorLogging {

  private[this] val defaultMapper: PartialFunction[Any, Any] = {
    case x: Any => x
  }

  def mapAndReply[T](value: Try[T])(mapper: Function[T, Any]): Unit = {
    sender ! (mapReply(value, mapper))
  }

  def reply[T](value: Try[T]): Unit = {
    sender ! (mapReply(value, defaultMapper))
  }
  
  def reply[T](f: Throwable): Unit = {
    sender ! Status.Failure(f)
  }

  def reply[T](future: Future[T])(implicit ec: ExecutionContext): Unit = {
    future onComplete {
      case s: Success[T] =>
        sender ! (mapReply(s, defaultMapper))
      case Failure(cause) =>
        Status.Failure(cause)
    }
  }

  def mapReply[T](t: Try[T], f: Function[T, Any]): Any = {
    t match {
      case Failure(cause) =>
        Status.Failure(cause)
      case Success(x) =>
        f(x)
    }
  }
}
