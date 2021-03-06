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

package com.convergencelabs.convergence.server.backend.services.domain.chat

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Signal, Terminated}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.util.actor.{ShardedActorStatUpPlan, ShardedActor, StartUpRequired}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}


/**
 * The [[ChatDeliveryActor]] handles delivery of outgoing chat
 * messages to all clients for a particular user.
 *
 * @param context     The ActorContext this actor is created in.
 * @param shardRegion The ActorRef to send messages to the chat share region.
 * @param shard       The ActorRef to send messages to this sharded actors host shard.
 */
private final class ChatDeliveryActor(domainId: DomainId,
                                      userId: DomainUserId,
                                      context: ActorContext[ChatDeliveryActor.Message],
                                      shardRegion: ActorRef[ChatDeliveryActor.Message],
                                      shard: ActorRef[ClusterSharding.ShardCommand])
  extends ShardedActor[ChatDeliveryActor.Message](
    context,
    shardRegion,
    shard,
    entityDescription = s"${domainId.namespace}/${domainId.domainId}/$userId") {

  import ChatDeliveryActor._

  private[this] var clients: Set[ActorRef[ChatClientActor.OutgoingMessage]] = Set()

  protected def initialize(message: Message): Try[ShardedActorStatUpPlan] = {
    Success(StartUpRequired)
  }

  def receiveInitialized(msg: Message): Behavior[Message] = {
    msg match {
      case ReceiveTimeout(_, _) =>
        this.onReceiveTimeout()
      case Subscribe(_, _, clientActor) =>
        context.watch(clientActor)
        clients += clientActor
        context.cancelReceiveTimeout()
        Behaviors.same
      case Unsubscribe(_, _, clientActor) =>
        onUnsubscribe(clientActor)
        Behaviors.same
      case Send(_, _, message) =>
        clients.foreach(_ ! message)
        Behaviors.same
    }
  }

  override protected def onTerminated(clientActor: ActorRef[Nothing]): Behavior[Message] = {
    onUnsubscribe(clientActor.unsafeUpcast[ChatClientActor.OutgoingMessage])
    Behaviors.same
  }

  private[this] def onUnsubscribe(clientActor: ActorRef[ChatClientActor.OutgoingMessage]): Unit = {
    context.unwatch(clientActor)
    clients -= clientActor
    if (clients.isEmpty) {
      context.setReceiveTimeout(FiniteDuration(5, TimeUnit.SECONDS), ReceiveTimeout(domainId, userId))
    }
  }

  private[this] def onReceiveTimeout(): Behavior[Message] = {
    debug("Asking shard region to passivate")
    this.passivate()
  }
}

object ChatDeliveryActor {

  def apply(domainId: DomainId,
            userId: DomainUserId,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand]): Behavior[Message] =
    Behaviors.setup(context => new ChatDeliveryActor(domainId, userId, context, shardRegion, shard))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable {
    val domainId: DomainId
    val userId: DomainUserId
  }

  /**
   * Signifies that a receive timeout occurred
   */
  private final case class ReceiveTimeout(domainId: DomainId, userId: DomainUserId) extends Message


  /**
   * Indicates that a client is interested in receiving chat messages for the
   * specified domain user.
   *
   * @param domainId    The domain the use belongs to.
   * @param userId      The user id of the user to get outgoing chat messages for.
   * @param clientActor The client actor the user is connected to.
   */
  final case class Subscribe(domainId: DomainId, userId: DomainUserId, clientActor: ActorRef[ChatClientActor.OutgoingMessage]) extends Message

  /**
   * Indicates that a client is no longer interested in receiving chat messages
   * for the specified domain user.
   *
   * @param domainId    The domain the use belongs to.
   * @param userId      The user id of the user the client belongs to.
   * @param clientActor The client actor the user is connected to.
   */
  final case class Unsubscribe(domainId: DomainId, userId: DomainUserId, clientActor: ActorRef[ChatClientActor.OutgoingMessage]) extends Message

  /**
   * Requests that the outgoing chat message is sent to all subsribed clients.
   *
   * @param domainId The domain the use belongs to.
   * @param userId   The user id of the user the client belongs to.
   * @param message  The message to send.
   */
  final case class Send(domainId: DomainId, userId: DomainUserId, message: ChatClientActor.OutgoingMessage) extends Message

}
