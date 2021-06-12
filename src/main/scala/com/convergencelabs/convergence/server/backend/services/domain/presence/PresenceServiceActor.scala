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

package com.convergencelabs.convergence.server.backend.services.domain.presence

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Signal, Terminated}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.server.api.realtime.PresenceClientActor
import com.convergencelabs.convergence.server.backend.services.domain.{DomainPersistenceManager, BaseDomainShardedActor}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.util.SubscriptionMap
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo, JsonTypeName}
import org.json4s.JsonAST.JValue

import scala.concurrent.duration.FiniteDuration

// FIXME This entire actor needs to be re-designed. This does not scale at all
//  we probably need to store presence state / data in the database so that
//  this can become stateless. Then we can use distributed pub-sub as mechanism
//  for persistence subscriptions.
final class PresenceServiceActor private(domainId: DomainId,
                                         context: ActorContext[PresenceServiceActor.Message],
                                         shardRegion: ActorRef[PresenceServiceActor.Message],
                                         shard: ActorRef[ClusterSharding.ShardCommand],
                                         domainPersistenceManager: DomainPersistenceManager,
                                         receiveTimeout: FiniteDuration)
  extends BaseDomainShardedActor[PresenceServiceActor.Message](domainId, context, shardRegion, shard, domainPersistenceManager, receiveTimeout) {


  private var presences = Map[DomainUserId, UserPresence]()

  // FIXME These are particularly problematic.  We are storing actor refs and userIds
  //  in memory.  there is not a good way to persist this.
  private val subscriptions = SubscriptionMap[ActorRef[PresenceClientActor.OutgoingMessage], DomainUserId]()
  private var clients = Map[ActorRef[PresenceClientActor.OutgoingMessage], DomainUserId]()

  import PresenceServiceActor._

  override def receiveInitialized(msg: PresenceServiceActor.Message): Behavior[Message] = {
    msg match {
      case msg: GetPresenceRequest =>
        onGetPresence(msg)
      case msg: GetPresencesRequest =>
        onGetPresences(msg)
      case msg: UserConnected =>
        onUserConnected(msg)
      case msg: SetUserPresenceState =>
        onSetState(msg)
      case msg: RemoveUserPresenceState =>
        onRemoveState(msg)
      case msg: ClearUserPresenceState =>
        onClearState(msg)
      case msg: SubscribePresenceRequest =>
        onSubscribe(msg)
      case UnsubscribePresence(_, userIds, client) =>
        onUnsubscribe(userIds, client)
      case ReceiveTimeout(_) =>
        this.passivate()
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = super.onSignal orElse {
    case Terminated(client) =>
      onUserDisconnected(client.asInstanceOf[ActorRef[PresenceClientActor.OutgoingMessage]])
  }

  private[this] def onGetPresences(msg: GetPresencesRequest): Behavior[Message] = {
    val GetPresencesRequest(_, userIds, replyTo) = msg
    val presence = lookupPresences(userIds)
    replyTo ! GetPresencesResponse(Right(presence))
    Behaviors.same
  }

  private[this] def onGetPresence(msg: GetPresenceRequest): Behavior[Message] = {
    val GetPresenceRequest(_, userId, replyTo) = msg
    val presence = lookupPresence(userId)
    replyTo ! GetPresenceResponse(Right(presence))
    Behaviors.same
  }

  private[this] def onUserConnected(msg: UserConnected): Behavior[Message] = {
    val UserConnected(_, userId, client) = msg

    this.subscriptions.subscribe(client, userId)

    clients += (client -> userId)
    val result = this.presences.get(userId) match {
      case Some(presence) =>
        presence.copy(clients = presence.clients + client)
      case None =>
        this.broadcastToSubscribed(userId, PresenceClientActor.UserPresenceAvailabilityChanged(userId, available = true))
        UserPresence(userId, available = true, Map(), Set(client))
    }

    context.watch(client)
    this.presences += (userId -> result)
    Behaviors.same
  }

  private[this] def onUserDisconnected(client: ActorRef[PresenceClientActor.OutgoingMessage]): Behavior[Message] = {
    this.subscriptions.unsubscribe(client)
    clients.get(client) match {
      case Some(userId) =>
        clients -= client
        this.presences.get(userId) match {
          case Some(presence) =>
            val updated = presence.copy(clients = presence.clients - client)
            if (updated.clients.isEmpty) {
              this.presences -= userId
              this.broadcastToSubscribed(userId, PresenceClientActor.UserPresenceAvailabilityChanged(userId, available = false))
            } else {
              this.presences += (userId -> updated)
            }
          case None =>
        }
      case None =>
    }
    Behaviors.same
  }

  private[this] def onSetState(msg: SetUserPresenceState): Behavior[Message] = {
    val SetUserPresenceState(_, userId, state) = msg
    this.presences.get(userId) match {
      case Some(presence) =>
        state foreach { case (k, v) =>
          this.presences += (userId -> presence.copy(state = presence.state + (k -> v)))
        }
        this.broadcastToSubscribed(userId, PresenceClientActor.UserPresenceStateSet(userId, state))
      case None =>
      // TODO Error
    }
    Behaviors.same
  }

  private[this] def onClearState(msg: ClearUserPresenceState): Behavior[Message] = {
    val ClearUserPresenceState(_, userId) = msg
    this.presences.get(userId) match {
      case Some(presence) =>
        this.presences += (userId -> presence.copy(state = Map()))
        this.broadcastToSubscribed(userId, PresenceClientActor.UserPresenceStateCleared(userId))
      case None =>
      // TODO Error
    }
    Behaviors.same
  }

  private[this] def onRemoveState(msg: RemoveUserPresenceState): Behavior[Message] = {
    val RemoveUserPresenceState(_, userId, keys) = msg
    this.presences.get(userId) match {
      case Some(presence) =>
        keys foreach { key =>
          this.presences += (userId -> presence.copy(state = presence.state - key))
        }
        this.broadcastToSubscribed(userId, PresenceClientActor.UserPresenceStateRemoved(userId, keys))
      case None =>
      // TODO Error
    }
    Behaviors.same
  }

  private[this] def onSubscribe(msg: SubscribePresenceRequest): Behavior[Message] = {
    val SubscribePresenceRequest(_, userIds, client, replyTo) = msg
    userIds.foreach { userId =>
      this.subscriptions.subscribe(client, userId)
    }
    replyTo ! SubscribePresenceResponse(Right(lookupPresences(userIds)))
    Behaviors.same
  }

  private[this] def onUnsubscribe(userIds: List[DomainUserId], client: ActorRef[PresenceClientActor.OutgoingMessage]): Behavior[Message] = {
    userIds.foreach(userId => this.subscriptions.unsubscribe(client, userId))
    Behaviors.same
  }

  private[this] def lookupPresences(userIds: List[DomainUserId]): List[UserPresence] = {
    userIds.map(lookupPresence)
  }

  private[this] def lookupPresence(userId: DomainUserId): UserPresence = {
    def defaultPresence: UserPresence = UserPresence(userId, available = false, Map(), Set())

    this.presences.getOrElse(userId, default = defaultPresence)
  }

  private[this] def broadcastToSubscribed(userId: DomainUserId, message: PresenceClientActor.OutgoingMessage): Unit = {
    val subscribers = this.subscriptions.subscribers(userId)
    subscribers foreach { client =>
      client ! message
    }
  }

  override protected def getDomainId(msg: Message): DomainId = msg.domainId

  override protected def getReceiveTimeoutMessage(): Message = ReceiveTimeout(this.domainId)
}

object PresenceServiceActor {
  def apply(domainId: DomainId,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration): Behavior[Message] = Behaviors.setup { context =>
    new PresenceServiceActor(
      domainId,
      context,
      shardRegion,
      shard,
      domainPersistenceManager,
      receiveTimeout)
  }

  sealed trait Message extends CborSerializable {
    val domainId: DomainId
  }

  private final case class ReceiveTimeout(domainId: DomainId) extends Message

  //
  // GetPresences
  //
  final case class GetPresencesRequest(domainId: DomainId,
                                       userIds: List[DomainUserId],
                                       replyTo: ActorRef[GetPresencesResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait GetPresencesError

  final case class GetPresencesResponse(presence: Either[GetPresencesError, List[UserPresence]]) extends CborSerializable

  //
  // GetPresence
  //
  final case class GetPresenceRequest(domainId: DomainId,
                                      userId: DomainUserId,
                                      replyTo: ActorRef[GetPresenceResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait GetPresenceError

  final case class GetPresenceResponse(presence: Either[GetPresenceError, UserPresence]) extends CborSerializable

  //
  // SubscribePresence
  //
  final case class SubscribePresenceRequest(domainId: DomainId,
                                            userIds: List[DomainUserId],
                                            client: ActorRef[PresenceClientActor.OutgoingMessage],
                                            replyTo: ActorRef[SubscribePresenceResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError]),
    new JsonSubTypes.Type(value = classOf[UnknownError]),
  ))
  sealed trait SubscribePresenceError

  final case class SubscribePresenceResponse(presences: Either[SubscribePresenceError, List[UserPresence]]) extends CborSerializable

  //
  // Unsubscribe
  //
  final case class UnsubscribePresence(domainId: DomainId,
                                       userIds: List[DomainUserId],
                                       client: ActorRef[PresenceClientActor.OutgoingMessage]) extends Message


  //
  // UserConnected
  //
  final case class UserConnected(domainId: DomainId,
                                 userId: DomainUserId,
                                 client: ActorRef[PresenceClientActor.OutgoingMessage]) extends Message

  //
  // SetUserPresenceState
  //
  final case class SetUserPresenceState(domainId: DomainId,
                                        userId: DomainUserId,
                                        state: Map[String, JValue]) extends Message

  //
  // RemoveUserPresenceState
  //
  final case class RemoveUserPresenceState(domainId: DomainId,
                                           userId: DomainUserId,
                                           keys: List[String]) extends Message

  //
  // ClearUserPresenceState
  //
  final case class ClearUserPresenceState(domainId: DomainId,
                                          userId: DomainUserId) extends Message


  //
  // Common Errors
  //
  @JsonTypeName("user_not_found")
  final case class UserNotFoundError(domainId: DomainId,
                                     userId: DomainUserId) extends GetPresenceError
    with GetPresencesError
    with SubscribePresenceError

  @JsonTypeName("unknown")
  final case class UnknownError() extends GetPresenceError
    with GetPresencesError
    with SubscribePresenceError

}
