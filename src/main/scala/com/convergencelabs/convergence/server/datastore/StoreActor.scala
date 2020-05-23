/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.datastore

import akka.actor.{Actor, ActorLogging, ActorRef, Status}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

abstract class StoreActor private[datastore] extends Actor with ActorLogging {

  private[this] val defaultMapper: PartialFunction[Any, Any] = {
    case x: Any => x
  }

  protected def mapAndReply[T](value: Try[T])(mapper: Function[T, Any]): Unit = {
    sender ! mapReply(value, mapper)
  }

  protected def reply[T](value: Try[T], replyTo: ActorRef): Unit = {
    val response = mapReply(value, defaultMapper)
    replyTo ! response
  }

  protected def reply[T](value: Try[T]): Unit = {
    reply(value, sender)
  }

  protected def reply[T](f: Throwable): Unit = {
    reply(f, sender)
  }

  protected def reply[T](f: Throwable, replyTo: ActorRef): Unit = {
    replyTo ! Status.Failure(f)
  }

  protected def reply[T](future: Future[T])(implicit ec: ExecutionContext): Unit = {
    mapAndReply(future)(defaultMapper)(ec)
  }

  protected def mapAndReply[T](future: Future[T])(mapper: Function[T, Any])(implicit ec: ExecutionContext): Unit = {
    val currentSender = sender
    future onComplete {
      case Success(s) =>
        currentSender ! mapper(s)
      case Failure(cause) =>
        currentSender ! Status.Failure(cause)
    }
  }

  protected def mapReply[T](t: Try[T], f: Function[T, Any]): Any = {
    t match {
      case Failure(cause) =>
        Status.Failure(cause)
      case Success(x) =>
        f(x)
    }
  }
}
