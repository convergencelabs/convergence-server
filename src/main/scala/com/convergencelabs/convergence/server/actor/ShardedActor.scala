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

package com.convergencelabs.convergence.server.actor

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.ClusterSharding.Passivate
import grizzled.slf4j.Logging

import scala.util.Try

sealed trait ShardedActorStatUpPlan

case object StartUpRequired extends ShardedActorStatUpPlan

case object StartUpNotRequired extends ShardedActorStatUpPlan

case object ShardedActorStop

object ShardedActor {
  val Uninitialized = "<uninitialized>"
}

/**
 * A helper class that standardizes how Sharded Actors behave within Convergence.
 *
 * @param context The ActorContext this actor is created in.
 * @tparam T The parameterized type of the message trait.
 */
abstract class ShardedActor[T](context: ActorContext[T],
                               shardRegion: ActorRef[T],
                               shard: ActorRef[ClusterSharding.ShardCommand])
  extends AbstractBehavior[T](context)
    with Logging {

  import ShardedActor._

  /**
   * A string that represents the identity of this actor. Used in logging.
   */
  protected var identityString: String = this.calculateIdentityString(Uninitialized)

  override def onMessage(msg: T): Behavior[T] = receiveUninitialized

  /**
   * Explicitly requests that this Actor passivate itself within the cluster.
   */
  protected def passivate(): Behavior[T] = {
    debug(s"$identityString: Passivating")
    shard ! Passivate(context.self)
    Behaviors.receiveMessage(receivePassivating)
  }

  /**
   * Receives the first message to this Actor when it is first
   * instantiated, it performs initialization logic and then
   * handles thee message.
   */
  private[this] val receiveUninitialized: Behavior[T] = Behaviors.receiveMessage { msg =>
    receiveInitialMessage(msg)
  }

  private[this] def receivePassivating(msg: T): Behavior[T] = {
    shardRegion ! msg
    Behaviors.same
  }

  /**
   * A helper method to process the first message sent to a Sharded Actor
   * It will set the identity of the Actor, attempt to initialize itself
   * and then continue to start up, assuming initialization was
   * successful. In certain cases, such was when an entity should not
   * actually exist, it may be determined that the actor should
   * immediately passivate.
   *
   * @param message The first message sent to this actor.
   */
  private[this] def receiveInitialMessage(message: T): Behavior[T] = {
    this.setIdentityData(message)
      .flatMap { identity =>
        this.identityString = calculateIdentityString(identity)
        debug(s"$identityString: Initializing.")
        this.initialize(message)
      }
      .map {
        case StartUpRequired =>
          debug(s"$identityString: Initialized, starting up.")
          this.receiveInitialized(message)
          Behaviors.receiveMessage(receiveInitialized)
        case StartUpNotRequired =>
          debug(s"$identityString: Initialized, but no start up required, passivating.")
          this.passivate()
      }
      .recover {
        case cause: Throwable =>
          error(s"Error initializing ShardedActor on first message: $message", cause)
          this.passivate()
      }.get
  }

  /**
   * A helper method to calculate this actor's identity string.
   *
   * @param identifier The unique portion of this actors identity.
   * @return A formatted identity string.
   */
  private[this] def calculateIdentityString(identifier: String): String = {
    s"${this.getClass.getSimpleName}($identifier)"
  }

  /**
   * A helper method to handle a request for this Sharded Actor to stop.
   */
  protected def stop(): Behavior[T] = {
    debug(s"$identityString: Received ShardedActorStop message, stopping.")
    Behaviors.stopped
  }

  override def onSignal: PartialFunction[Signal, Behavior[T]] = {
    case PostStop =>
      postStop()
      Behaviors.same
  }

  protected def postStop(): Unit = {
    debug(s"$identityString: Stopped")
  }

  /**
   * Allows the actor to set its internal state relative to it identifying
   * information contained with in a message and to return a unique string
   * identifier. The message passed to this method will be the first
   * message sent to this actor which caused it to spawn.
   *
   * @param message The message containing identity data.
   * @return Success with the unique identity string if the operation was
   *         successful, Failure otherwise.
   */
  protected def setIdentityData(message: T): Try[String]

  /**
   * Asks the actor to initialize based on the first message that
   * was sent to the actor.
   *
   * @param message The first message sent to the actor.
   * @return The desired startup action, or a Failure.
   */
  protected def initialize(message: T): Try[ShardedActorStatUpPlan]

  /**
   * A receive method with which to receive messages once the actor is
   * initialized.
   *
   * @return The desired Receive behavior.
   */
  protected def receiveInitialized(msg: T): Behavior[T]
}
