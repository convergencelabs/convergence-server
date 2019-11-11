package com.convergencelabs.server.api.realtime

import akka.actor.{Actor, ActorLogging, ActorRef, Props, actorRef2Scala}
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.presence._
import com.convergencelabs.server.api.realtime.ImplicitMessageConversions.userPresenceToMessage
import com.convergencelabs.server.domain.DomainUserSessionId
import com.convergencelabs.server.domain.presence._
import com.convergencelabs.server.util.concurrent.AskFuture

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

object PresenceClientActor {
  def props(presenceServiceActor: ActorRef, session: DomainUserSessionId): Props =
    Props(new PresenceClientActor(presenceServiceActor, session))
}

//  TODO: Add connect / disconnect logic
class PresenceClientActor(presenceServiceActor: ActorRef, session: DomainUserSessionId) extends Actor with ActorLogging {

  import akka.pattern.ask
  private[this] implicit val timeout: Timeout = Timeout(5 seconds)
  private[this] implicit val ec: ExecutionContextExecutor = context.dispatcher

  presenceServiceActor ! UserConnected(session.userId, self)

  def receive: Receive = {
    // Incoming messages from the client
    case MessageReceived(message) if message.isInstanceOf[NormalMessage with PresenceMessage] =>
      onMessageReceived(message.asInstanceOf[NormalMessage with PresenceMessage])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[RequestMessage with PresenceMessage] =>
      onRequestReceived(message.asInstanceOf[RequestMessage with PresenceMessage], replyPromise)

    // Outgoing messages from the presence subsystem
    case UserPresenceSetState(userId, state) =>
      context.parent ! PresenceStateSetMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)), JsonProtoConverter.jValueMapToValueMap(state))
    case UserPresenceRemoveState(userId, keys) =>
      context.parent ! PresenceStateRemovedMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)), keys)
    case UserPresenceClearState(userId) =>
      context.parent ! PresenceStateClearedMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)))
    case UserPresenceAvailability(userId, available) =>
      context.parent ! PresenceAvailabilityChangedMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)), available)

    case msg: Any =>
      log.warning(s"Invalid message received by the presence client: $msg")
  }

  //
  // Incoming Messages
  //
  def onMessageReceived(message: NormalMessage with PresenceMessage): Unit = {
    message match {
      case setState: PresenceSetStateMessage => onPresenceStateSet(setState)
      case removeState: PresenceRemoveStateMessage => onPresenceStateRemoved(removeState)
      case _: PresenceClearStateMessage => onPresenceStateCleared()
      case unsubPresence: UnsubscribePresenceMessage => onUnsubscribePresence(unsubPresence)
      case msg: Any =>
        log.warning(s"Invalid incoming presence message: $msg")
    }
  }

  def onPresenceStateSet(message: PresenceSetStateMessage): Unit = {
    val PresenceSetStateMessage(state) = message
    this.presenceServiceActor ! UserPresenceSetState(session.userId, JsonProtoConverter.valueMapToJValueMap(state))
  }

  def onPresenceStateRemoved(message: PresenceRemoveStateMessage): Unit = {
    val PresenceRemoveStateMessage(keys) = message
    this.presenceServiceActor ! UserPresenceRemoveState(session.userId, keys.toList)
  }

  def onPresenceStateCleared(): Unit = {
    this.presenceServiceActor ! UserPresenceClearState(session.userId)
  }

  def onUnsubscribePresence(message: UnsubscribePresenceMessage): Unit = {
    val UnsubscribePresenceMessage(userIdData) = message
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId)
    this.presenceServiceActor ! UnsubscribePresence(userIds.toList, self)
  }

  def onRequestReceived(message: RequestMessage with PresenceMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case presenceReq: PresenceRequestMessage => onPresenceRequest(presenceReq, replyCallback)
      case subscribeReq: SubscribePresenceRequestMessage => onSubscribeRequest(subscribeReq, replyCallback)
    }
  }

  def onPresenceRequest(request: PresenceRequestMessage, cb: ReplyCallback): Unit = {
    val PresenceRequestMessage(userIdData) = request
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId)
    val future = this.presenceServiceActor ? PresenceRequest(userIds.toList)

    future.mapResponse[List[UserPresence]] onComplete {
      case Success(userPresences) =>
        cb.reply(PresenceResponseMessage(userPresences.map(userPresenceToMessage)))
      case Failure(_) =>
        cb.unexpectedError("Could not retrieve presence")
    }
  }

  def onSubscribeRequest(request: SubscribePresenceRequestMessage, cb: ReplyCallback): Unit = {
    val SubscribePresenceRequestMessage(userIdData) = request
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId)
    val future = this.presenceServiceActor ? SubscribePresence(userIds.toList, self)

    future.mapResponse[List[UserPresence]] onComplete {
      case Success(userPresences) =>
        cb.reply(SubscribePresenceResponseMessage(userPresences.map(userPresenceToMessage)))
      case Failure(_) =>
        cb.unexpectedError("Could not subscribe to presence")
    }
  }
}
