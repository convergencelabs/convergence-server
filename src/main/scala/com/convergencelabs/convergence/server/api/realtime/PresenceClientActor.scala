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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.presence._
import com.convergencelabs.convergence.server.actor.{AskUtils, CborSerializable}
import com.convergencelabs.convergence.server.api.realtime.ImplicitMessageConversions.userPresenceToMessage
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection.ReplyCallback
import com.convergencelabs.convergence.server.domain.presence._
import com.convergencelabs.convergence.server.domain.{DomainUserId, DomainUserSessionId}
import grizzled.slf4j.Logging
import org.json4s.JsonAST.JValue
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

//  TODO: Add connect / disconnect logic
class PresenceClientActor private(context: ActorContext[PresenceClientActor.Message],
                                  session: DomainUserSessionId,
                                  clientActor: ActorRef[ClientActor.SendServerMessage],
                                  presenceServiceActor: ActorRef[PresenceServiceActor.Message],
                                  private[this] implicit val defaultTimeout: Timeout)
  extends AbstractBehavior[PresenceClientActor.Message](context) with Logging with AskUtils {

  import PresenceClientActor._

  private[this] implicit val ec: ExecutionContextExecutor = context.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system

  presenceServiceActor ! PresenceServiceActor.UserConnected(session.userId, context.self)

  override def onMessage(msg: PresenceClientActor.Message): Behavior[PresenceClientActor.Message] = {
    msg match {
      case msg: IncomingMessage =>
        onIncomingMessage(msg)
      case msg: OutgoingMessage =>
        onOutgoingMessage(msg)
    }
  }

  private[this] def onIncomingMessage(msg: PresenceClientActor.IncomingMessage): Behavior[Message] = {
    msg match {
      case IncomingProtocolMessage(message) =>
        onMessageReceived(message)
      case IncomingProtocolRequest(message, replyPromise)  =>
        onRequestReceived(message, replyPromise)
    }

    Behaviors.same
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
    }
  }

  private[this] def onPresenceStateSet(message: PresenceSetStateMessage): Unit = {
    val PresenceSetStateMessage(state, _) = message
    this.presenceServiceActor ! PresenceServiceActor.SetUserPresenceState(session.userId, JsonProtoConverter.valueMapToJValueMap(state))
  }

  private[this] def onPresenceStateRemoved(message: PresenceRemoveStateMessage): Unit = {
    val PresenceRemoveStateMessage(keys, _) = message
    this.presenceServiceActor ! PresenceServiceActor.RemoveUserPresenceState(session.userId, keys.toList)
  }

  private[this] def onPresenceStateCleared(): Unit = {
    this.presenceServiceActor ! PresenceServiceActor.ClearUserPresenceState(session.userId)
  }

  private[this] def onUnsubscribePresence(message: UnsubscribePresenceMessage): Unit = {
    val UnsubscribePresenceMessage(userIdData, _) = message
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId)
    this.presenceServiceActor ! PresenceServiceActor.UnsubscribePresence(userIds.toList, context.self)
  }

  private[this] def onRequestReceived(message: RequestMessage with PresenceMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case presenceReq: PresenceRequestMessage => onPresenceRequest(presenceReq, replyCallback)
      case subscribeReq: SubscribePresenceRequestMessage => onSubscribeRequest(subscribeReq, replyCallback)
    }
  }

  private[this] def onPresenceRequest(request: PresenceRequestMessage, cb: ReplyCallback): Unit = {
    val PresenceRequestMessage(userIdData, _) = request
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId)
    presenceServiceActor.ask[PresenceServiceActor.GetPresencesResponse](PresenceServiceActor.GetPresencesRequest(userIds.toList, _))
        .map(_.presence.fold({
          case PresenceServiceActor.UserNotFoundError(userId) =>
            userNotFound(userId, cb)
          case _ =>
            cb.unexpectedError("An unexpected error occurred getting presence.")
        }, { presences =>
          cb.reply(PresenceResponseMessage(presences.map(userPresenceToMessage)))
        }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onSubscribeRequest(request: SubscribePresenceRequestMessage, cb: ReplyCallback): Unit = {
    val SubscribePresenceRequestMessage(userIdData, _) = request
    val userIds = userIdData.map(ImplicitMessageConversions.dataToDomainUserId)
    presenceServiceActor.ask[PresenceServiceActor.SubscribePresenceResponse](PresenceServiceActor.SubscribePresenceRequest(userIds.toList, context.self.narrow[OutgoingMessage], _))
      .map(_.presences.fold({
        case PresenceServiceActor.UserNotFoundError(userId) =>
          userNotFound(userId, cb)
        case _ =>
          cb.unexpectedError("An unexpected error occurred subscribing to presence.")
      }, { presences =>
        cb.reply(SubscribePresenceResponseMessage(presences.map(userPresenceToMessage)))
      }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def userNotFound(userId: DomainUserId, cb: ReplyCallback): Unit = {
    cb.expectedError(ErrorCodes.UserNotFound, s"A user with id '${userId.username}' does not exist.")
  }

  //
  // Outgoing Messages
  //
  private[this] def onOutgoingMessage(msg: PresenceClientActor.OutgoingMessage): Behavior[Message] = {
    val serverMessage: GeneratedMessage with ServerMessage with NormalMessage = msg match {
      case UserPresenceStateSet(userId, state) =>
        PresenceStateSetMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)), JsonProtoConverter.jValueMapToValueMap(state))
      case UserPresenceStateRemoved(userId, keys) =>
        PresenceStateRemovedMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)), keys)
      case UserPresenceStateCleared(userId) =>
        PresenceStateClearedMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)))
      case UserPresenceAvailabilityChanged(userId, available) =>
        PresenceAvailabilityChangedMessage(Some(ImplicitMessageConversions.domainUserIdToData(userId)), available)
    }

    clientActor ! ClientActor.SendServerMessage(serverMessage)

    Behaviors.same
  }
}

object PresenceClientActor {
  private[realtime] def apply(session: DomainUserSessionId,
            clientActor: ActorRef[ClientActor.SendServerMessage],
            presenceServiceActor: ActorRef[PresenceServiceActor.Message],
            defaultTimeout: Timeout
           ): Behavior[Message] =
    Behaviors.setup(context => new PresenceClientActor(context, session, clientActor, presenceServiceActor, defaultTimeout))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  //
  // Messages from the client
  //
  private[realtime] sealed trait IncomingMessage extends Message

  private[realtime] type IncomingNormalMessage = GeneratedMessage with NormalMessage with PresenceMessage with ClientMessage

  private[realtime] final case class IncomingProtocolMessage(message: IncomingNormalMessage) extends IncomingMessage

  private[realtime] type IncomingRequestMessage = GeneratedMessage with RequestMessage with PresenceMessage with ClientMessage

  private[realtime] final case class IncomingProtocolRequest(message: IncomingRequestMessage, replyCallback: ReplyCallback) extends IncomingMessage


  //
  // Messages from within the server
  //
  sealed trait OutgoingMessage extends Message with CborSerializable

  final case class UserPresenceStateSet(userId: DomainUserId, state: Map[String, JValue]) extends OutgoingMessage

  final case class UserPresenceStateRemoved(userId: DomainUserId, keys: List[String]) extends OutgoingMessage

  final case class UserPresenceStateCleared(userId: DomainUserId) extends OutgoingMessage

  final case class UserPresenceAvailabilityChanged(userId: DomainUserId, available: Boolean) extends OutgoingMessage

}