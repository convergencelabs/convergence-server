package com.convergencelabs.server.frontend.realtime;

import scala.collection.mutable.Queue
import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.domain.IdentityResolutionRequest
import com.convergencelabs.server.domain.IdentityResolutionResponse

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.util.Timeout
import io.convergence.proto.chat.ChatChannelEventData
import io.convergence.proto.identity.IdentityCacheUpdateMessage
import io.convergence.proto.message.ConvergenceMessage
import io.convergence.proto.message.ConvergenceMessage.Body
import akka.actor.Props
import scala.collection.mutable.LinkedList
import scala.collection.mutable.ListBuffer

class MessageRecord(val message: ConvergenceMessage, var ready: Boolean)

object IdentityCacheManager {
  def props(
    clientActor: ActorRef,
    identityServiceActor: ActorRef,
    timeout: Timeout): Props = {
    Props(new IdentityCacheManager(clientActor, identityServiceActor, timeout))
  }
}

class IdentityCacheManager(
  private[this] val clientActor: ActorRef,
  private[this] val identityServiceActor: ActorRef,
  private[this] implicit val timeout: Timeout) extends Actor with ActorLogging {

  import akka.pattern.ask

  private[this] val sessions: Set[String] = Set()
  private[this] val users: Set[String] = Set()

  private[this] val messages: Queue[MessageRecord] = Queue()

  private[this] implicit val ec = context.dispatcher

  def receive: Receive = {
    case message: ConvergenceMessage =>
      onConvergenceMessage(message)
    case message: IdentityResolved =>
      onIdentityResolved(message)
    case msg: Any =>
      this.unhandled(msg)
  }

  private[this] def onConvergenceMessage(message: ConvergenceMessage): Unit = {
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

  private[this] def processChatEvent(message: ConvergenceMessage, chatData: Seq[ChatChannelEventData]): Unit = {
    val usernames = scala.collection.mutable.HashSet[String]()
    chatData.map {
      _.event match {
        case ChatChannelEventData.Event.Created(created) =>
         usernames ++= created.members
         usernames += created.username
        case ChatChannelEventData.Event.Message(chatMessage) =>
          usernames += chatMessage.username
        case ChatChannelEventData.Event.UserAdded(userAdded) =>
          usernames += userAdded.username
          usernames += userAdded.addedUser
        case ChatChannelEventData.Event.UserRemoved(userRemoved) =>
          usernames += userRemoved.username
          usernames += userRemoved.removedUser
        case ChatChannelEventData.Event.UserJoined(userJoined) =>
          usernames += userJoined.username
        case ChatChannelEventData.Event.UserLeft(userLeft) =>
          usernames += userLeft.username
        case ChatChannelEventData.Event.NameChanged(nameChanged) =>
          usernames += nameChanged.username
        case ChatChannelEventData.Event.TopicChanged(topicChanged) =>
          usernames += topicChanged.username
        case ChatChannelEventData.Event.Empty =>
          ???
      }
    }
    processMessage(message, Set(), usernames.toSet)
  }

  private[this] def processMessage(
    message: ConvergenceMessage,
    sessionIds: Set[String],
    usernames: Set[String]): Unit = {
    val requiredSessions = sessionIds.diff(this.sessions)
    val requiredUsernames = usernames.diff(this.users)

    if (requiredSessions.isEmpty && requiredUsernames.isEmpty) {
      val record = new MessageRecord(message, true)
      this.messages.enqueue(record)
      this.flushQueue()
    } else {
      val record = new MessageRecord(message, false)
      this.messages.enqueue(record)
      val request = IdentityResolutionRequest(requiredSessions, requiredUsernames)
      (identityServiceActor ? request)
        .mapTo[IdentityResolutionResponse]
        .onComplete {
          case Success(response) =>
            self ! IdentityResolved(record, response)
          case Failure(cause) =>
            cause.printStackTrace()
        }
    }
  }

  private[this] def onIdentityResolved(message: IdentityResolved): Unit = {
    val IdentityResolved(record, response) = message
    val users = response.users.map(ImplicitMessageConversions.mapDomainUser(_))
    val body = IdentityCacheUpdateMessage()
      .withSessions(response.sessionMap)
      .withUsers(users.toSeq)
    val updateMessage = ConvergenceMessage()
      .withIdentityCacheUpdate(body)
    this.clientActor ! SendProcessedMessage(updateMessage)

    record.ready = true
    this.flushQueue()
  }

  private[this] def flushQueue(): Unit = {
    while (!this.messages.isEmpty && this.messages.front.ready) {
      val message = this.messages.dequeue()
      this.clientActor ! SendProcessedMessage(message.message)
    }
  }
}

case class IdentityResolved(record: MessageRecord, response: IdentityResolutionResponse)
