package com.convergencelabs.server.actor

import akka.actor.Actor
import akka.actor.PoisonPill
import akka.cluster.sharding.ShardRegion.Passivate

abstract class ShardedActor[T](
  private[this] val messageClass: Class[T]) extends Actor {

  override def receive(): Receive = receiveUninitialized

  private[this] def receiveUninitialized: Receive = {
    case msg if messageClass.isInstance(msg) => recevieInitialMessage(msg.asInstanceOf[T])
    case _ => this.unhandled(_)
  }

  private[this] def receivePassivating: Receive = {
    case _ => this.context.parent.forward(_)
  }

  protected def passivate(): Unit = {
    this.context.parent.tell(Passivate(PoisonPill), this.self);
    this.context.become(this.receivePassivating);
  }

  private[this] def recevieInitialMessage(message: T): Unit = {
    this.initialize(message);
    this.receiveInitialized(message);
    this.context.become(this.receiveInitialized);
  }

  protected def receiveInitialized: Receive

  protected def initialize(message: T): Unit
}
