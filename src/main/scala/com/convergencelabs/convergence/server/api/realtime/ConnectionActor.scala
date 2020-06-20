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

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Terminated}

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
 */
private[realtime] object ConnectionActor {

  /**
   * @param clientActor The client actor this connection is owned by.
   */
  private[realtime] def apply(clientActor: ActorRef[ClientActor.ConnectionMessage],
            socketActor: Option[ActorRef[WebSocketService.WebSocketMessage]]): Behavior[Message] =
    Behaviors.receive[Message] { (context, message: Message) =>
      message match {
        case IncomingBinaryMessage(data) =>
          clientActor ! ClientActor.IncomingBinaryMessage(data)
          Behaviors.same

        case OutgoingBinaryMessage(data) =>
          socketActor.foreach(_ ! WebSocketService.OutgoingBinaryMessage(data))
          Behaviors.same

        case WebSocketOpened(actor) =>
          clientActor ! ClientActor.ConnectionOpened(context.self.narrow[ConnectionActor.ClientMessage])
          ConnectionActor(clientActor, Some(actor))

        case WebSocketClosed =>
          clientActor ! ClientActor.ConnectionClosed
          Behaviors.stopped

        case WebSocketError(cause) =>
          clientActor ! ClientActor.ConnectionError(cause)
          Behaviors.stopped

        case CloseConnection =>
          disconnect(socketActor)
      }
    }.receiveSignal {
      case (_, Terminated(actor)) if actor == clientActor =>
        disconnect(socketActor)
    }

  private[this] def disconnect(socketActor: Option[ActorRef[WebSocketService.WebSocketMessage]]): Behavior[Message] = {
    socketActor.foreach(_ ! WebSocketService.CloseSocket)
    Behaviors.stopped
  }

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  private[realtime] sealed trait Message

  private[realtime] sealed trait WebSocketMessage extends Message

  /**
   * Indicates that the connection should now be open and use he supplied
   * ActorRef to send outgoing messages too.
   *
   * @param outgoingMessageSink The ActorRef to use to send outgoing messages
   *                            to the Web Socket.
   */
  private[realtime] case class WebSocketOpened(outgoingMessageSink: ActorRef[WebSocketService.WebSocketMessage]) extends WebSocketMessage

  /**
   * Indicates that the Web Socket for this connection has been closed.
   */
  private[realtime] case object WebSocketClosed extends WebSocketMessage

  /**
   * Indicates that the Web Socket associated with this connection emitted an
   * error.
   *
   * @param cause The cause of the error.
   */
  private[realtime] case class WebSocketError(cause: Throwable) extends WebSocketMessage


  /**
   * Represents an incoming binary message from the client.
   *
   * @param data The incoming binary web socket message data.
   */
  private[realtime] case class IncomingBinaryMessage(data: Array[Byte]) extends WebSocketMessage


  private[realtime] sealed trait ClientMessage extends Message

  /**
   * Indicates that this connection should be closed. This message does not
   * come from the web socket, but rather from within Convergence.
   */
  private[realtime] case object CloseConnection extends ClientMessage

  /**
   * Represents an outgoing binary message from the client.
   *
   * @param data The outgoing binary web socket message data.
   */
  private[realtime] case class OutgoingBinaryMessage(data: Array[Byte]) extends ClientMessage

}
