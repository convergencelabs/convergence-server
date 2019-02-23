package com.convergencelabs.server.api.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.domain.presence.PresenceRequest
import com.convergencelabs.server.domain.presence.SubscribePresence
import com.convergencelabs.server.domain.presence.UnsubscribePresence
import com.convergencelabs.server.domain.presence.UserConnected
import com.convergencelabs.server.domain.presence.UserPresence
import com.convergencelabs.server.domain.presence.UserPresenceAvailability
import com.convergencelabs.server.domain.presence.UserPresenceClearState
import com.convergencelabs.server.domain.presence.UserPresenceRemoveState
import com.convergencelabs.server.domain.presence.UserPresenceSetState
import com.convergencelabs.server.api.realtime.ImplicitMessageConversions.userPresenceToMessage
import com.convergencelabs.server.util.concurrent.AskFuture

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.util.Timeout
import io.convergence.proto.Normal
import io.convergence.proto.Presence
import io.convergence.proto.Request
import io.convergence.proto.presence.PresenceAvailabilityChangedMessage
import io.convergence.proto.presence.PresenceClearStateMessage
import io.convergence.proto.presence.PresenceRemoveStateMessage
import io.convergence.proto.presence.PresenceRequestMessage
import io.convergence.proto.presence.PresenceResponseMessage
import io.convergence.proto.presence.PresenceSetStateMessage
import io.convergence.proto.presence.PresenceStateClearedMessage
import io.convergence.proto.presence.PresenceStateRemovedMessage
import io.convergence.proto.presence.PresenceStateSetMessage
import io.convergence.proto.presence.SubscribePresenceRequestMessage
import io.convergence.proto.presence.SubscribePresenceResponseMessage
import io.convergence.proto.presence.UnsubscribePresenceMessage
import com.convergencelabs.server.domain.DomainUserSessionId

object PresenceClientActor {
  def props(presenceServiceActor: ActorRef, session: DomainUserSessionId): Props =
    Props(new PresenceClientActor(presenceServiceActor, session))
}

//  TODO: Add connect / disconnect logic
class PresenceClientActor(presenceServiceActor: ActorRef, session: DomainUserSessionId) extends Actor with ActorLogging {

  import akka.pattern.ask
  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  presenceServiceActor ! UserConnected(session.userId, self)

  def receive: Receive = {
    case MessageReceived(message) if message.isInstanceOf[Normal with Presence] =>
      onMessageReceived(message.asInstanceOf[Normal with Presence])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[Request with Presence] =>
      onRequestReceived(message.asInstanceOf[Request with Presence], replyPromise)

    // TODO: Add available messages
    case UserPresenceSetState(userId, state) =>
      context.parent ! PresenceStateSetMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)), JsonProtoConverter.jValueMapToValueMap(state))
    case UserPresenceRemoveState(userId, keys) =>
      context.parent ! PresenceStateRemovedMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)), keys)
    case UserPresenceClearState(userId) =>
      context.parent ! PresenceStateClearedMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)))
    case UserPresenceAvailability(userId, available) =>
      context.parent ! PresenceAvailabilityChangedMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)), available)

    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: Normal with Presence): Unit = {
    message match {
      case setState: PresenceSetStateMessage => onPresenceStateSet(setState)
      case removeState: PresenceRemoveStateMessage => onPresenceStateRemoved(removeState)
      case clearState: PresenceClearStateMessage => onPresenceStateCleared()
      case unsubPresence: UnsubscribePresenceMessage => onUnsubscribePresence(unsubPresence)
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
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId(_))
    this.presenceServiceActor ! UnsubscribePresence(userIds.toList, self)
  }

  def onRequestReceived(message: Request with Presence, replyCallback: ReplyCallback): Unit = {
    message match {
      case presenceReq: PresenceRequestMessage => onPresenceRequest(presenceReq, replyCallback)
      case subscribeReq: SubscribePresenceRequestMessage => onSubscribeRequest(subscribeReq, replyCallback)
    }
  }

  def onPresenceRequest(request: PresenceRequestMessage, cb: ReplyCallback): Unit = {
    val PresenceRequestMessage(userIdData) = request
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId(_))
    val future = this.presenceServiceActor ? PresenceRequest(userIds.toList)

    future.mapResponse[List[UserPresence]] onComplete {
      case Success(userPresences) =>
        cb.reply(PresenceResponseMessage(userPresences.toSeq.map(userPresenceToMessage(_))))
      case Failure(cause) =>
        cb.unexpectedError("Could not retrieve presence")
    }
  }

  def onSubscribeRequest(request: SubscribePresenceRequestMessage, cb: ReplyCallback): Unit = {
    val SubscribePresenceRequestMessage(userIdData) = request
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId(_))
    val future = this.presenceServiceActor ? SubscribePresence(userIds.toList, self)

    future.mapResponse[List[UserPresence]] onComplete {
      case Success(userPresences) =>
        cb.reply(SubscribePresenceResponseMessage(userPresences.toSeq.map(userPresenceToMessage(_))))
      case Failure(cause) =>
        cb.unexpectedError("Could not subscribe to presence")
    }
  }
}
