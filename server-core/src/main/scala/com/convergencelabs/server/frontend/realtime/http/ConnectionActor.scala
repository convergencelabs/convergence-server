package com.convergencelabs.server.frontend.realtime.http

import scala.concurrent.duration.FiniteDuration
import akka.actor.ActorLogging
import akka.actor.Actor
import java.util.concurrent.TimeUnit
import akka.actor.ActorRef
import akka.actor.Cancellable


case class Disconnected()
case class Connected(ref: ActorRef)

class ConnectionActor extends Actor with ActorLogging {
  var sourceRef: Option[ActorRef] = None
  var pingTask: Option[Cancellable] = None

  def receive: Receive = {
    case IncomingMessage(message) ⇒
      if (message != "ignore") {
        dispatch("Echo: " + message)
      }

    case Connected(ref) =>
      println(s"connected: ${ref}")
      sourceRef = Some(ref)
      implicit val ec = context.dispatcher
      pingTask = Some(context.system.scheduler.schedule(
        FiniteDuration(2, TimeUnit.SECONDS),
        FiniteDuration(2, TimeUnit.SECONDS)) {
          dispatch("ping")
        })

    case Disconnected() =>
      println(s"cleanly disconnected: ${sourceRef}")
      pingTask.get.cancel()
      sourceRef = None
      this.context.stop(this.self)

    case akka.actor.Status.Failure =>
      println(s"uncleanly disconnected: ${sourceRef}")
      sourceRef = None
      this.context.stop(this.self)
  }

  def dispatch(msg: String): Unit = sourceRef.get ! OutgoingMessage(msg)
}