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
import com.convergencelabs.convergence.server.actor.CborSerializable

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
  import ConnectionActor._

  private[this] var socketActor: Option[ActorRef] = None

  this.context.watch(clientActor)

  def receive: Receive = {
    case incoming: IncomingBinaryMessage =>
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
  /**
   * Creates a new ConnectionActor for a specific ClientActor.
   *
   * @param clientActor The ClientActor this connection will be bound to.
   *
   * @return The new ConnectionActor.
   */
  def props(clientActor: ActorRef): Props = Props(new ConnectionActor(clientActor))

  /**
   * Indicates that the connection should now be open and use he supplied
   * ActorRef to send outgoing messages too.
   *
   * @param outgoingMessageSink The ActorRef to use to send outgoing messages
   *                            to the Web Socket.
   */
  private[realtime] case class WebSocketOpened(outgoingMessageSink: ActorRef) extends CborSerializable

  /**
   * Indicates that the Web Socket for this connection has been closed.
   */
  private[realtime] case object WebSocketClosed extends CborSerializable

  /**
   * Indicates that the Web Socket associated with this connection emitted an
   * error.
   *
   * @param cause The cause of the error.
   */
  private[realtime] case class WebSocketError(cause: Throwable) extends CborSerializable

  /**
   * Indicates that this connection should be closed. This message does not
   * come from the web socket, but rather from within Convergence.
   */
  private[realtime] case object CloseConnection extends CborSerializable

  /**
   * Represents an incoming binary message from the client.
   *
   * @param message The incoming binary web socket message data.
   */
  private[realtime] case class IncomingBinaryMessage(message: Array[Byte]) extends CborSerializable

  /**
   * Represents an outgoing binary message from the client.
   *
   * @param message The outgoing binary web socket message data.
   */
  private[realtime] case class OutgoingBinaryMessage(message: Array[Byte]) extends CborSerializable
}
