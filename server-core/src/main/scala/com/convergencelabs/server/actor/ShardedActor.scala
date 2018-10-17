package com.convergencelabs.server.actor

import akka.actor.Actor
import akka.actor.PoisonPill
import akka.cluster.sharding.ShardRegion.Passivate
import scala.util.Try
import akka.actor.ActorLogging

abstract class ShardedActor[T](
  private[this] val messageClass: Class[T])
  extends Actor
  with ActorLogging {

  override def receive(): Receive = receiveUninitialized

  private[this] def receiveUninitialized: Receive = {
    case msg if messageClass.isInstance(msg) => recevieInitialMessage(msg.asInstanceOf[T])
    case _                                   => this.unhandled(_)
  }

  private[this] def receivePassivating: Receive = {
    case msg if messageClass.isInstance(msg)  => this.context.parent.forward(msg)
  }

  protected def passivate(): Unit = {
    this.context.parent.tell(Passivate(PoisonPill), this.self)
    this.context.become(this.receivePassivating)
  }

  private[this] def recevieInitialMessage(message: T): Unit = {
    this.initialize(message)
      .map(_ => {
        this.receiveInitialized(message)
        this.context.become(this.receiveInitialized)
      })
      .recover {
        case cause: Throwable =>
          log.error(cause, s"Error initializing SharededActor on first message: ${message}")
          this.context.stop(this.self)
      }
  }

  protected def receiveInitialized: Receive

  protected def initialize(message: T): Try[Unit]
}
