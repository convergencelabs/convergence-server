package com.convergencelabs.server.actor

import scala.util.Try

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.PoisonPill
import akka.cluster.sharding.ShardRegion.Passivate

abstract class ShardedActor[T](
  private[this] val messageClass: Class[T])
  extends Actor
  with ActorLogging {

  override def receive(): Receive = receiveUninitialized

  private[this] def receiveUninitialized: Receive = {
    case msg if messageClass.isInstance(msg) => recevieInitialMessage(msg.asInstanceOf[T])
    case _ => this.unhandled(_)
  }

  private[this] def receivePassivating: Receive = {
    case msg if messageClass.isInstance(msg) => this.context.parent.forward(msg)
  }

  protected def passivate(): Unit = {
    this.context.parent ! Passivate(PoisonPill)
    this.context.become(this.receivePassivating)
  }

  private[this] def recevieInitialMessage(message: T): Unit = {
    this.initialize(message)
      .map(_ match {
        case StartUpRequired =>
          this.context.become(this.receiveInitialized)
          this.receiveInitialized(message)
        case StartUpNotRequired =>
          this.context.stop(this.self)
      })
      .recover {
        case cause: Throwable =>
          log.error(cause, s"Error initializing SharededActor on first message: ${message}")
          this.context.stop(this.self)
      }
  }

  protected def receiveInitialized: Receive

  protected def initialize(message: T): Try[ShardedActorStatUpPlan]
}

sealed trait ShardedActorStatUpPlan
case object StartUpRequired extends ShardedActorStatUpPlan
case object StartUpNotRequired extends ShardedActorStatUpPlan
