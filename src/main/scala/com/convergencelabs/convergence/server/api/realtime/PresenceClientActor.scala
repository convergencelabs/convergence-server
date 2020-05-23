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

import akka.actor.{Actor, ActorLogging, ActorRef, Props, actorRef2Scala}
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.presence._
import com.convergencelabs.convergence.server.api.realtime.ImplicitMessageConversions.userPresenceToMessage
import com.convergencelabs.convergence.server.domain.DomainUserSessionId
import com.convergencelabs.convergence.server.domain.presence._
import com.convergencelabs.convergence.server.util.concurrent.AskFuture

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

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

  private[this] def onPresenceStateSet(message: PresenceSetStateMessage): Unit = {
    val PresenceSetStateMessage(state) = message
    this.presenceServiceActor ! UserPresenceSetState(session.userId, JsonProtoConverter.valueMapToJValueMap(state))
  }

  private[this] def onPresenceStateRemoved(message: PresenceRemoveStateMessage): Unit = {
    val PresenceRemoveStateMessage(keys) = message
    this.presenceServiceActor ! UserPresenceRemoveState(session.userId, keys.toList)
  }

  private[this] def onPresenceStateCleared(): Unit = {
    this.presenceServiceActor ! UserPresenceClearState(session.userId)
  }

  private[this] def onUnsubscribePresence(message: UnsubscribePresenceMessage): Unit = {
    val UnsubscribePresenceMessage(userIdData) = message
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId)
    this.presenceServiceActor ! UnsubscribePresence(userIds.toList, self)
  }

  private[this] def onRequestReceived(message: RequestMessage with PresenceMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case presenceReq: PresenceRequestMessage => onPresenceRequest(presenceReq, replyCallback)
      case subscribeReq: SubscribePresenceRequestMessage => onSubscribeRequest(subscribeReq, replyCallback)
    }
  }

  private[this] def onPresenceRequest(request: PresenceRequestMessage, cb: ReplyCallback): Unit = {
    val PresenceRequestMessage(userIdData) = request
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId)
    val future = this.presenceServiceActor ? GetPresenceRequest(userIds.toList)

    future.mapResponse[GetPresenceResponse] onComplete {
      case Success(GetPresenceResponse(userPresences)) =>
        cb.reply(PresenceResponseMessage(userPresences.map(userPresenceToMessage)))
      case Failure(_) =>
        cb.unexpectedError("Could not retrieve presence")
    }
  }

  private[this] def onSubscribeRequest(request: SubscribePresenceRequestMessage, cb: ReplyCallback): Unit = {
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

object PresenceClientActor {
  def props(presenceServiceActor: ActorRef, session: DomainUserSessionId): Props =
    Props(new PresenceClientActor(presenceServiceActor, session))
}