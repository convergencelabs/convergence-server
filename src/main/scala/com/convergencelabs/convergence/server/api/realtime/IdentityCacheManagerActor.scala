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

package com.convergencelabs.convergence.server.api.realtime

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import com.convergencelabs.convergence.proto.ConvergenceMessage._
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.chat._
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.proto.identity._
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.api.realtime.ClientActor.IdentityResolutionError
import com.convergencelabs.convergence.server.domain.{DomainUserId, IdentityServiceActor}
import grizzled.slf4j.Logging

import scala.collection.mutable
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

/**
 * A helper class that tracks which identities a particular client is aware of
 * and ensures that the client is made aware of a users identity before a message
 * about that user is sent over the wire.
 *
 * @param context              The ActorContext for this actor.
 * @param clientActor          The client actor for the client this objects is supporting.
 * @param identityServiceActor The actor that is used to resolve identity.
 * @param timeout              The timeout to user for identity resolution requests.
 */
class IdentityCacheManagerActor private(context: ActorContext[IdentityCacheManagerActor.Message],
                                        clientActor: ActorRef[ClientActor.FromIdentityResolver],
                                        identityServiceActor: ActorRef[IdentityServiceActor.IdentityResolutionRequest],
                                        private[this] implicit val timeout: Timeout)
  extends AbstractBehavior[IdentityCacheManagerActor.Message](context) with Logging {

  import IdentityCacheManagerActor._
  import ImplicitMessageConversions._

  private[this] val sessions: Set[String] = Set()
  private[this] val users: Set[DomainUserId] = Set()

  private[this] val messages: mutable.Queue[MessageRecord] = mutable.Queue()

  private[this] implicit val ec: ExecutionContextExecutor = context.executionContext

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case OutgoingMessage(message) =>
        onConvergenceMessage(message)
        Behaviors.same

      case message: IdentityResolved =>
        onIdentityResolved(message)
        Behaviors.same

      case message: IdentityResolutionFailure =>
        onIdentityResolutionFailure(message)
        Behaviors.same
    }
  }

  private[this] def onConvergenceMessage(message: ConvergenceMessage): Unit = {
    message.body match {
      case Body.OpenRealTimeModelResponse(body) =>
        val sessions = body.connectedClients.toSet ++ body.resyncingClients.toSet
        processMessage(message, sessions, Set())
      case Body.RemoteClientOpenedModel(body) =>
        processMessage(message, Set(body.sessionId), Set())
      case Body.RemoteOperation(body) =>
        processMessage(message, Set(body.sessionId), Set())
      case Body.ModelResyncServerComplete(body) =>
        val sessions = body.connectedClients.toSet ++ body.resyncingClients.toSet
        processMessage(message, sessions, Set())
      case Body.RemoteClientResyncStarted(body) =>
        processMessage(message, Set(body.sessionId), Set())
      case Body.RemoteClientResyncCompleted(body) =>
        processMessage(message, Set(body.sessionId), Set())
      case Body.HistoricalOperationsResponse(body) =>
        processMessage(message, body.operations.map(op => op.sessionId).toSet, Set())
      case Body.GetModelPermissionsResponse(body) =>
        processMessage(message, Set(), body.userPermissions.keys.toSet)

      // Activity
      case Body.ActivityParticipantsResponse(body) =>
        processMessage(message, body.state.keys.toSet, Set())
      case Body.ActivityJoinResponse(body) =>
        processMessage(message, body.state.keys.toSet, Set())
      case Body.ActivitySessionJoined(body) =>
        processMessage(message, Set(body.sessionId), Set())

      // Presence
      case Body.PresenceResponse(body) =>
        processMessage(message, Set(), body.userPresences.map(p => dataToDomainUserId(p.user.get)).toSet)
      case Body.PresenceSubscribeResponse(body) =>
        processMessage(message, Set(), body.userPresences.map(p => dataToDomainUserId(p.user.get)).toSet)

      // Chat
      case Body.GetChatsResponse(body) =>
        val users = body.chatInfo.flatMap(_.members.map(m => dataToDomainUserId(m.user.get))).toSet
        processMessage(message, Set(), users)
      case Body.GetDirectChatsResponse(body) =>
        val users = body.chatInfo.flatMap(_.members.map(m => dataToDomainUserId(m.user.get))).toSet
        processMessage(message, Set(), users)
      case Body.GetJoinedChatsResponse(body) =>
        val users = body.chatInfo.flatMap(_.members.map(m => dataToDomainUserId(m.user.get))).toSet
        processMessage(message, Set(), users)
      case Body.JoinChatResponse(body) =>
        val users = body.chatInfo.get.members.map(m => ImplicitMessageConversions.dataToDomainUserId(m.user.get)).toSet
        processMessage(message, Set(), users)
      case Body.UserJoinedChat(body) =>
        processMessage(message, Set(), Set(body.user.get))
      case Body.UserLeftChat(body) =>
        processMessage(message, Set(), Set(body.user.get))
      case Body.UserAddedToChat(body) =>
        processMessage(message, Set(), Set(body.user.get, body.addedUser.get))
      case Body.UserRemovedFromChat(body) =>
        processMessage(message, Set(), Set(body.user.get, body.removedUser.get))
      case Body.ChatNameChanged(body) =>
        processMessage(message, Set(), Set(body.user.get))
      case Body.ChatTopicChanged(body) =>
        processMessage(message, Set(), Set(body.user.get))
      case Body.ChatEventsMarkedSeen(body) =>
        processMessage(message, Set(), Set(body.user.get))
      case Body.RemoteChatMessage(body) =>
        processMessage(message, Set(), Set(body.user.get))
      case Body.GetChatHistoryResponse(body) =>
        processChatEvent(message, body.data)
      case Body.ChatsSearchResponse(body) =>
        val users = body.data.flatMap(info => info.members.map(m => dataToDomainUserId(m.user.get))).toSet
        processMessage(message, Set(), users)

      // permissions
      case Body.GetAllUserPermissionsResponse(body) =>
        processMessage(message, Set(), body.users.map(u => dataToDomainUserId(u.user.get)).toSet)

      case _ =>
        this.messages.enqueue(MessageRecord(message, ready = true))
        this.flushQueue()
    }
  }

  private[this] def processChatEvent(message: ConvergenceMessage, chatData: Seq[ChatEventData]): Unit = {
    val usernames = scala.collection.mutable.HashSet[DomainUserIdData]()
    chatData.map {
      _.event match {
        case ChatEventData.Event.Created(created) =>
          usernames ++= created.members
          usernames += created.user.get
        case ChatEventData.Event.Message(chatMessage) =>
          usernames += chatMessage.user.get
        case ChatEventData.Event.UserAdded(userAdded) =>
          usernames += userAdded.user.get
          usernames += userAdded.addedUser.get
        case ChatEventData.Event.UserRemoved(userRemoved) =>
          usernames += userRemoved.user.get
          usernames += userRemoved.removedUser.get
        case ChatEventData.Event.UserJoined(userJoined) =>
          usernames += userJoined.user.get
        case ChatEventData.Event.UserLeft(userLeft) =>
          usernames += userLeft.user.get
        case ChatEventData.Event.NameChanged(nameChanged) =>
          usernames += nameChanged.user.get
        case ChatEventData.Event.TopicChanged(topicChanged) =>
          usernames += topicChanged.user.get
        case ChatEventData.Event.Empty =>
          // FIXME send an error back.
          ???
      }
    }
    processMessage(message, Set(), usernames.map(dataToDomainUserId).toSet)
  }

  private[this] def processMessage(message: ConvergenceMessage,
                                   sessionIds: Set[String],
                                   usernames: Set[DomainUserId]): Unit = {
    val requiredSessions = sessionIds.diff(this.sessions)
    val requiredUsers = usernames.diff(this.users)

    if (requiredSessions.isEmpty && requiredUsers.isEmpty) {
      val record = MessageRecord(message, ready = true)
      this.messages.enqueue(record)
      this.flushQueue()
    } else {
      val record = MessageRecord(message, ready = false)
      this.messages.enqueue(record)

      context.ask(identityServiceActor, (r: ActorRef[IdentityServiceActor.IdentityResolutionResponse]) =>
        IdentityServiceActor.IdentityResolutionRequest(requiredSessions, requiredUsers, r)) {
        case Success(response) =>
          IdentityResolved(record, response)
        case Failure(cause) =>
          IdentityResolutionFailure(record, cause)
      }
    }
  }

  private[this] def onIdentityResolved(message: IdentityResolved): Unit = {
    val IdentityResolved(record, response) = message
    response.resolution.fold(
      {
        case IdentityServiceActor.UnknownError() =>
          this.clientActor ! IdentityResolutionError()
      },
      { resolution =>
        val users = resolution.users.map(ImplicitMessageConversions.mapDomainUser)
        val body = IdentityCacheUpdateMessage()
          .withSessions(resolution.sessionMap.map { case (sessionId, userId) => (sessionId, ImplicitMessageConversions.domainUserIdToData(userId)) })
          .withUsers(users.toSeq)
        val updateMessage = ConvergenceMessage()
          .withIdentityCacheUpdate(body)
        this.clientActor ! ClientActor.SendProcessedMessage(updateMessage)

        record.ready = true
        this.flushQueue()
      })
  }

  private[this] def onIdentityResolutionFailure(message: IdentityResolutionFailure): Unit = {
    val IdentityResolutionFailure(record, cause) = message

    error(s"Failed to resolve identity for message: ${record.message}", cause)
    this.clientActor ! ClientActor.IdentityResolutionError()
  }

  private[this] def flushQueue(): Unit = {
    while (this.messages.nonEmpty && this.messages.front.ready) {
      val message = this.messages.dequeue()
      this.clientActor ! ClientActor.SendProcessedMessage(message.message)
    }
  }
}

private[realtime] object IdentityCacheManagerActor {

  private[realtime] def apply(clientActor: ActorRef[ClientActor.FromIdentityResolver],
            identityServiceActor: ActorRef[IdentityServiceActor.IdentityResolutionRequest],
            timeout: Timeout): Behavior[Message] =
    Behaviors.setup(context => new IdentityCacheManagerActor(context, clientActor, identityServiceActor, timeout))

  private final case class MessageRecord(message: ConvergenceMessage, var ready: Boolean)

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  private final case class IdentityResolved(record: MessageRecord, response: IdentityServiceActor.IdentityResolutionResponse) extends Message

  private final case class IdentityResolutionFailure(record: MessageRecord, cause: Throwable) extends Message

  final case class OutgoingMessage(message: ConvergenceMessage) extends Message
}
