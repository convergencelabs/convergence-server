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

package com.convergencelabs.convergence.server.api.realtime

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated, actorRef2Scala}

case object WebSocketClosed
case class WebSocketOpened(ref: ActorRef)
case class WebSocketError(cause: Throwable)
case object CloseConnection

/**
 * The [[ConnectionActor]] is a light weight actor that will receive
 * web socket messages from the Akka HTTP Subsystem and forward them
 * to the client actor. Conversely thee client actor will send this
 * actor messages to forward on to the Akka HTTP system. This actor
 * is essentially a bridge between the Akka HTTP web socket API
 * and the Convergence Actors.
 *
 * The client actor reference is provided at construction time.  The
 * web socket actor reference will be supplied through a message as
 * the connection is completed.
 *
 * @param clientActor The client actor this connection is owned by.
 */
class ConnectionActor(clientActor: ActorRef) extends Actor with ActorLogging {
  private[this] var socketActor: Option[ActorRef] = None

  this.context.watch(clientActor)

  def receive: Receive = {
    case incoming: IncomingBinaryMessage ⇒
      clientActor ! incoming

    case outgoing: OutgoingBinaryMessage =>
      socketActor.foreach(_ ! outgoing)

    case WebSocketOpened(actor) =>
      socketActor = Some(actor)
      clientActor ! WebSocketOpened(self)

    case WebSocketClosed =>
      socketActor = None
      clientActor ! WebSocketClosed
      this.context.stop(this.self)

    case akka.actor.Status.Failure(cause) =>
      socketActor = None
      clientActor ! WebSocketError(cause)
      this.context.stop(self)

    case CloseConnection =>
      closeConnection()

    case Terminated(actor) if actor == clientActor =>
      closeConnection()
  }
  
  private[this] def closeConnection(): Unit = {
    socketActor.foreach(_ ! PoisonPill)
      this.context.stop(self)
  }
}

object ConnectionActor {
  def props(clientActor: ActorRef): Props = Props(new ConnectionActor(clientActor))
}
