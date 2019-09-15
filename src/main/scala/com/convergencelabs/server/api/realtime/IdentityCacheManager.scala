package com.convergencelabs.server.api.realtime;

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
import io.convergence.proto.chat.ChatEventData
import io.convergence.proto.identity.IdentityCacheUpdateMessage
import io.convergence.proto.message.ConvergenceMessage
import io.convergence.proto.message.ConvergenceMessage.Body
import akka.actor.Props
import scala.collection.mutable.ListBuffer
import com.convergencelabs.server.domain.DomainUserId
import io.convergence.proto.identity.DomainUserIdData

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
  import ImplicitMessageConversions._

  private[this] val sessions: Set[String] = Set()
  private[this] val users: Set[DomainUserId] = Set()

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
      case Body.UserAddedToChatChannel(body) =>
        processMessage(message, Set(), Set(body.user.get, body.addedUser.get))
      case Body.UserRemovedFromChatChannel(body) =>
        processMessage(message, Set(), Set(body.user.get, body.removedUser.get))
      case Body.ChatNameChanged(body) =>
        processMessage(message, Set(), Set(body.user.get))
      case Body.ChatTopicChanged(body) =>
        processMessage(message, Set(), Set(body.user.get))
      case Body.ChatEventsMarkedSeen(body) =>
        processMessage(message, Set(), Set(body.user.get))
      case Body.RemoteChatMessage(body) =>
        processMessage(message, Set(body.sessionId), Set())
      case Body.GetChatHistoryResponse(body) =>
        processChatEvent(message, body.eventData)

      // permissions
      case Body.GetAllUserPermissionsResponse(body) =>
        processMessage(message, Set(), body.users.map(u => dataToDomainUserId(u.user.get)).toSet)
      case body =>
        this.messages.enqueue(new MessageRecord(message, true))
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
          ???
      }
    }
    processMessage(message, Set(), usernames.map(dataToDomainUserId(_)).toSet)
  }

  private[this] def processMessage(
    message: ConvergenceMessage,
    sessionIds: Set[String],
    usernames: Set[DomainUserId]): Unit = {
    val requiredSessions = sessionIds.diff(this.sessions)
    val requiredUsers = usernames.diff(this.users)

    if (requiredSessions.isEmpty && requiredUsers.isEmpty) {
      val record = new MessageRecord(message, true)
      this.messages.enqueue(record)
      this.flushQueue()
    } else {
      val record = new MessageRecord(message, false)
      this.messages.enqueue(record)
      val request = IdentityResolutionRequest(requiredSessions, requiredUsers)
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
      .withSessions(response.sessionMap.map{case (sessionId, userId) => (sessionId, ImplicitMessageConversions.domainUserIdToData(userId))})
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
