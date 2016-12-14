package com.convergencelabs.server.datastore

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Status
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import akka.actor.ActorRef

abstract class StoreActor private[datastore] extends Actor with ActorLogging {

  private[this] val defaultMapper: PartialFunction[Any, Any] = {
    case x: Any => x
  }

  def mapAndReply[T](value: Try[T])(mapper: Function[T, Any]): Unit = {
    sender ! (mapReply(value, mapper))
  }

  def reply[T](value: Try[T], sender: ActorRef): Unit = {
    sender ! (mapReply(value, defaultMapper))
  }
  
  def reply[T](value: Try[T]): Unit = {
    reply(value, sender)
  }
  
  def reply[T](f: Throwable): Unit = {
    reply(f, sender)
  }
  
  def reply[T](f: Throwable, sender: ActorRef): Unit = {
    sender ! Status.Failure(f)
  }

  def reply[T](future: Future[T])(implicit ec: ExecutionContext): Unit = {
    mapAndReply(future)(defaultMapper)(ec)
  }
  
  def mapAndReply[T](future: Future[T])(mapper: Function[T, Any])(implicit ec: ExecutionContext): Unit = {
    val currentSender = sender
    future onComplete {
      case Success(s) =>
        currentSender ! mapper(s)
      case Failure(cause) =>
        currentSender ! Status.Failure(cause)
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
