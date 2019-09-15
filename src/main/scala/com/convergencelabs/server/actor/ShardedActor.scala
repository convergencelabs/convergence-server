package com.convergencelabs.server.actor

import scala.util.Try

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.cluster.sharding.ShardRegion.Passivate

sealed trait ShardedActorStatUpPlan
case object StartUpRequired extends ShardedActorStatUpPlan
case object StartUpNotRequired extends ShardedActorStatUpPlan

case object ShardedActorStop;

object ShardedActor {
  val Uninitialied = "<uninitialized>"
}

abstract class ShardedActor[T](
  private[this] val messageClass: Class[T])
  extends Actor
  with ActorLogging {

  import ShardedActor._

  protected var identityString = this.calculateIdentityString(Uninitialied)

  override def receive(): Receive = receiveUninitialized

  protected def passivate(): Unit = {
    log.debug(s"${identityString}: Passivating")
    this.context.parent ! Passivate(stopMessage = ShardedActorStop)
    this.context.become(this.receivePassivating)
  }

  private[this] def receiveUninitialized: Receive = {
    case msg if messageClass.isInstance(msg) =>
      recevieInitialMessage(msg.asInstanceOf[T])
    case _ =>
      this.unhandled(_)
  }

  private[this] def receivePassivating: Receive = {
    case ShardedActorStop =>
      this.onStop()
    case msg if messageClass.isInstance(msg) =>
      this.context.parent.forward(msg)
  }

  private[this] def recevieInitialMessage(message: T): Unit = {
    this.setIdentityData(message)
      .flatMap { identity =>
        this.identityString = calculateIdentityString(identity)
        log.debug(s"${identityString}: Initializing.")
        this.initialize(message)
      }
      .map(_ match {
        case StartUpRequired =>
          log.debug(s"${identityString}: Initialized, starting up.")
          this.context.become(this.receiveInitialized)
          this.receiveInitialized(message)
        case StartUpNotRequired =>
          log.debug(s"${identityString}: Initialized, but no start up required, passivating.")
          this.passivate()
      })
      .recover {
        case cause: Throwable =>
          log.error(cause, s"Error initializing SharededActor on first message: ${message}")
          this.passivate()
      }
  }

  private[this] def calculateIdentityString(identifier: String): String = {
    s"${this.getClass.getSimpleName}(${identifier})"
  }

  private[this] def onStop(): Unit = {
    log.debug(s"${identityString}: Received ShardedActorStop message, stopping.")
    this.context.stop(self)
  }

  override def postStop(): Unit = {
    log.debug(s"${identityString}: Stopped")
  }

  protected def setIdentityData(message: T): Try[String];

  protected def initialize(message: T): Try[ShardedActorStatUpPlan]

  protected def receiveInitialized: Receive
}
