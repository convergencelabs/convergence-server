package com.convergencelabs.server.frontend.realtime;

import io.convergence.proto.message.ConvergenceMessage
import io.convergence.proto.message.ConvergenceMessage.Body
import io.convergence.proto.model.OpenRealtimeModelResponseMessage
import io.convergence.proto.model.RemoteClientOpenedMessage
import io.convergence.proto.operations.RemoteOperationMessage
import io.convergence.proto.model.HistoricalOperationsResponseMessage
import io.convergence.proto.chat.ChatChannelEventData
import com.convergencelabs.server.domain.IdentityResolutionRequest
import com.convergencelabs.server.domain.IdentityResolutionResponse

import scala.collection.mutable.Queue

import akka.actor.ActorRef
import akka.util.Timeout
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext
import io.convergence.proto.identity.IdentityCacheUpdateMessage

class MessageRecord(val message: ConvergenceMessage, var ready: Boolean)

class IdentityCacheManager(
  private[this] val clientActor: ActorRef,
  private[this] val identityServiceActor: ActorRef,
  private[this] implicit val timeout: Timeout,
  private[this] implicit val ec: ExecutionContext) {

  import akka.pattern.ask

  private[this] val sessions: Set[String] = Set()
  private[this] val users: Set[String] = Set()

  private[this] val messages: Queue[MessageRecord] = Queue()

  def onConvergenceMessage(message: ConvergenceMessage): Unit = {
    message.body match {
      case Body.OpenRealTimeModelResponse(body) =>
        val sessions = body.connectedClients.toSet
        processMessage(message, sessions, Set())
      case Body.RemoteClientOpenedModel(body) =>
        processMessage(message, Set(body.sessionId), Set())
      case Body.RemoteOperation(body) =>
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
        processMessage(message, Set(), body.userPresences.map(_.username).toSet)
      case Body.PresenceSubscribeResponse(body) =>
        processMessage(message, Set(), body.userPresences.map(_.username).toSet)

      // Chat
      case Body.GetChatChannelsResponse(body) =>
        processMessage(message, Set(), body.channelInfo.flatMap(_.members.map(_.username)).toSet)
      case Body.GetDirectChatChannelsResponse(body) =>
        processMessage(message, Set(), body.channelInfo.flatMap(_.members.map(_.username)).toSet)
      case Body.GetJoinedChatChannelsResponse(body) =>
        processMessage(message, Set(), body.channelInfo.flatMap(_.members.map(_.username)).toSet)
      case Body.JoinChatChannelResponse(body) =>
        val foo = body.channelInfo.map(_.members.map(_.username).toSet)
        processMessage(message, Set(), foo.getOrElse(Set()))
      case Body.UserJoinedChatChannel(body) =>
        processMessage(message, Set(), Set(body.username))
      case Body.UserLeftChatChannel(body) =>
        processMessage(message, Set(), Set(body.username))
      case Body.UserAddedToChatChannel(body) =>
        processMessage(message, Set(), Set(body.username, body.addedUser))
      case Body.UserRemovedFromChatChannel(body) =>
        processMessage(message, Set(), Set(body.username, body.removedUser))
      case Body.ChatChannelNameChanged(body) =>
        processMessage(message, Set(), Set(body.username))
      case Body.ChatChannelTopicChanged(body) =>
        processMessage(message, Set(), Set(body.username))
      case Body.ChatChannelEventsMarkedSeen(body) =>
        processMessage(message, Set(), Set(body.username))
      case Body.RemoteChatMessage(body) =>
        processMessage(message, Set(body.sessionId), Set())
      case Body.GetChatChannelHistoryResponse(body) =>
        processChatEvent(message, body.eventData)

      // permissions
      case Body.GetAllUserPermissionsResponse(body) =>
        processMessage(message, Set(), body.users.map { case (username, permissions) => username }.toSet)
      case body =>
        this.messages.enqueue(new MessageRecord(message, true))
        this.flushQueue()
    }
  }

  def processChatEvent(message: ConvergenceMessage, chatData: Seq[ChatChannelEventData]): Unit = {
    chatData.map {
      _.event match {
        case ChatChannelEventData.Event.Created(created) =>
          val usernames = created.members.toSet + created.username
          processMessage(message, Set(), usernames)
        case ChatChannelEventData.Event.Message(chatMessage) =>
          processMessage(message, Set(), Set(chatMessage.username))
        case ChatChannelEventData.Event.UserAdded(userAdded) =>
          processMessage(message, Set(), Set(userAdded.username, userAdded.addedUser))
        case ChatChannelEventData.Event.UserRemoved(userRemoved) =>
          processMessage(message, Set(), Set(userRemoved.username, userRemoved.removedUser))
        case ChatChannelEventData.Event.UserJoined(userJoined) =>
          processMessage(message, Set(), Set(userJoined.username))
        case ChatChannelEventData.Event.UserLeft(userLeft) =>
          processMessage(message, Set(), Set(userLeft.username))
        case ChatChannelEventData.Event.NameChanged(nameChanged) =>
          processMessage(message, Set(), Set(nameChanged.username))
        case ChatChannelEventData.Event.TopicChanged(topicChanged) =>
          processMessage(message, Set(), Set(topicChanged.username))
        case ChatChannelEventData.Event.Empty =>
          ???
      }
    }
  }

  def processMessage(
    message: ConvergenceMessage,
    sessionIds: Set[String],
    usernames: Set[String]): Unit = {
    val requiredSessions = sessionIds.diff(this.sessions)
    val requiredUsernames = usernames.diff(this.users)

    if (requiredSessions.isEmpty && requiredUsernames.isEmpty) {
      val record = new MessageRecord(message, true)
      this.messages.enqueue(record)
    } else {
      val record = new MessageRecord(message, false)
      this.messages.enqueue(record)
      val request = IdentityResolutionRequest(requiredSessions, requiredUsernames)
      (identityServiceActor ? request)
        .mapTo[IdentityResolutionResponse]
        .onComplete {
          case Success(response) =>
            val users = response.users.map(ImplicitMessageConversions.mapDomainUser(_))
            val body = IdentityCacheUpdateMessage()
              .withSessions(response.sessionMap)
              .withUsers(users.toSeq)
            val updateMessage = ConvergenceMessage()
              .withIdentityCacheUpdate(body)
            this.clientActor ! SendProcessedMessage(updateMessage)
            
            record.ready = true
            this.flushQueue()
          case Failure(cause) =>
            cause.printStackTrace()
        }
    }
  }

  def flushQueue(): Unit = {
    while (!this.messages.isEmpty && this.messages.front.ready) {
      val message = this.messages.dequeue()
      this.clientActor ! SendProcessedMessage(message.message)
    }
  }
}
