package com.convergencelabs.server.api.realtime

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.Terminated
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
