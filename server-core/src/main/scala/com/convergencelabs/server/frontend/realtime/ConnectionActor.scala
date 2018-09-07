package com.convergencelabs.server.frontend.realtime

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.actorRef2Scala

case object WebSocketClosed
case class WebSocketOpened(ref: ActorRef)
case class WebSocketError(cause: Throwable)
case object CloseConnection

object ConnectionActor {
  def props(clientActor: ActorRef): Props = Props(new ConnectionActor(clientActor))
}

class ConnectionActor(clientActor: ActorRef) extends Actor with ActorLogging {
  var socketActor: Option[ActorRef] = None

  def receive: Receive = {
    case incoming: IncomingBinaryMessage ⇒
      clientActor ! incoming

    case outgoing: OutgoingBinaryMessage =>
      socketActor.get ! outgoing

    case WebSocketOpened(ref) =>
      socketActor = Some(ref)
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
      socketActor.get ! PoisonPill
      this.context.stop(self)
  }
}
