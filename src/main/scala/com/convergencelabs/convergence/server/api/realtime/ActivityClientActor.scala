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
import com.convergencelabs.convergence.proto.activity._
import com.convergencelabs.convergence.server.actor.{AskUtils, CborSerializable}
import com.convergencelabs.convergence.server.api.realtime.ActivityClientActor.Message
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection.ReplyCallback
import com.convergencelabs.convergence.server.domain.activity.ActivityActor
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserSessionId}
import grizzled.slf4j.Logging
import org.json4s.JsonAST.JValue
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

/**
 * The ActivityClientActor handles messages to and from the client for the
 * Activity subsystem. It is generally spawned by a [[ClientActor]] and the
 * [[ClientActor]] will forward all Activity related messages to this
 * child actor.
 *
 * @param context             The ActorContext for this actor.
 * @param activityShardRegion The shard region that contains ActivityActor entities.
 * @param clientActor         The ClientActor that this ActivityClientActor is a child of.
 * @param domain              The id of the domain that the user connected to.
 * @param session             The session id of the connected client.
 */
class ActivityClientActor private(context: ActorContext[Message],
                                  activityShardRegion: ActorRef[ActivityActor.Message],
                                  clientActor: ActorRef[ClientActor.SendServerMessage],
                                  domain: DomainId,
                                  session: DomainUserSessionId,
                                  defaultTimeout: Timeout)
  extends AbstractBehavior[Message](context) with Logging with AskUtils {

  import ActivityClientActor._

  private[this] implicit val timeout: Timeout = defaultTimeout
  private[this] implicit val ec: ExecutionContextExecutor = context.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system

  override def onMessage(msg: ActivityClientActor.Message): Behavior[Message] = {
    msg match {
      case msg: IncomingMessage =>
        onIncomingMessage(msg)
      case msg: OutgoingMessage =>
        onOutgoingMessage(msg)
    }
  }

  //
  // Incoming Messages
  //

  private[this] def onIncomingMessage(msg: ActivityClientActor.IncomingMessage): Behavior[Message] = {
    msg match {
      case IncomingProtocolMessage(message) =>
        onMessageReceived(message)
      case IncomingProtocolRequest(message, replyCallback) =>
        onRequestReceived(message, replyCallback)
    }

    Behaviors.same
  }


  private[this] def onMessageReceived(message: IncomingNormalMessage): Unit = {
    message match {
      case setState: ActivityUpdateStateMessage =>
        onActivityUpdateState(setState)
    }
  }

  private[this] def onActivityUpdateState(message: ActivityUpdateStateMessage): Unit = {
    val ActivityUpdateStateMessage(id, state, complete, removed, _) = message
    val mappedState = JsonProtoConverter.valueMapToJValueMap(state)
    val updateMessage = ActivityActor.UpdateState(domain, id, session.sessionId, mappedState, complete, removed.toList)
    this.activityShardRegion ! updateMessage
  }

  private[this] def onRequestReceived(message: IncomingRequestMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case join: ActivityJoinRequestMessage =>
        onActivityJoin(join, replyCallback)
      case leave: ActivityLeaveRequestMessage =>
        onActivityLeave(leave, replyCallback)
      case participant: ActivityParticipantsRequestMessage =>
        onParticipantsRequest(participant, replyCallback)
    }
  }

  private[this] def onParticipantsRequest(RequestMessage: ActivityParticipantsRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityParticipantsRequestMessage(activityId, _) = RequestMessage
    activityShardRegion.ask[ActivityActor.GetParticipantsResponse](
      ActivityActor.GetParticipantsRequest(domain, activityId, _))
      .map { response =>
        cb.reply(ActivityParticipantsResponseMessage(response.state.map {
          case (k, v) => k -> ActivityStateData(JsonProtoConverter.jValueMapToValueMap(v))
        }))
      }
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onActivityJoin(RequestMessage: ActivityJoinRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityJoinRequestMessage(activityId, state, _) = RequestMessage
    val jsonState = JsonProtoConverter.valueMapToJValueMap(state)
    activityShardRegion
      .ask[ActivityActor.JoinResponse](
        ActivityActor.JoinRequest(domain, activityId, session.sessionId, jsonState, context.self.narrow[OutgoingMessage], _))
      .map(_.response.fold({
        case ActivityActor.AlreadyJoined() =>
          cb.expectedError(ErrorCodes.ActivityAlreadyJoined, s"The session is already joined to activity '$activityId'.")
      }, { response =>
        val mappedState = response.state.view.mapValues(v => ActivityStateData(JsonProtoConverter.jValueMapToValueMap(v))).toMap
        cb.reply(ActivityJoinResponseMessage(mappedState))
      }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onActivityLeave(message: ActivityLeaveRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityLeaveRequestMessage(activityId, _) = message
    activityShardRegion
      .ask[ActivityActor.LeaveResponse](
        ActivityActor.LeaveRequest(domain, activityId, session.sessionId, _))
      .map(_.response.fold({
        case ActivityActor.NotJoinedError() =>
         cb.expectedError(ErrorCodes.ActivityNotJoined, s"The session is not joined to activity '$activityId'.")
      }, { _ =>
        cb.reply(ActivityLeaveResponseMessage())
        // todo reply
      }))
      .recoverWith(handleAskFailure(_, cb))

  }

  //
  // Outgoing Messages
  //

  private[this] def onOutgoingMessage(msg: ActivityClientActor.OutgoingMessage): Behavior[Message] = {
    val serverMessage: GeneratedMessage with ServerMessage with NormalMessage = msg match {
      case ActivitySessionJoined(activityId, sessionId, state) =>
        ActivitySessionJoinedMessage(activityId, sessionId, JsonProtoConverter.jValueMapToValueMap(state))
      case ActivitySessionLeft(activityId, sessionId) =>
        ActivitySessionLeftMessage(activityId, sessionId)
      case ActivityStateUpdated(activityId, sessionId, state, complete, removed) =>
        ActivityStateUpdatedMessage(
          activityId, sessionId, JsonProtoConverter.jValueMapToValueMap(state), complete, removed)
    }

    clientActor ! ClientActor.SendServerMessage(serverMessage)

    Behaviors.same
  }
}

object ActivityClientActor {
  private[realtime] def apply(domain: DomainId,
            session: DomainUserSessionId,
            clientActor: ActorRef[ClientActor.SendServerMessage],
            activityServiceActor: ActorRef[ActivityActor.Message],
            defaultTimeout: Timeout
           ): Behavior[ActivityClientActor.Message] =
    Behaviors.setup(context => new ActivityClientActor(context, activityServiceActor, clientActor, domain, session, defaultTimeout))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message

  //
  // Messages from the client
  //
  private[realtime] sealed trait IncomingMessage extends Message

  private[realtime] type IncomingNormalMessage = GeneratedMessage with NormalMessage with ActivityMessage with ClientMessage

  private[realtime] final case class IncomingProtocolMessage(message: IncomingNormalMessage) extends IncomingMessage

  private[realtime] type IncomingRequestMessage = GeneratedMessage with RequestMessage with ActivityMessage with ClientMessage

  private[realtime] final case class IncomingProtocolRequest(message: IncomingRequestMessage, replyCallback: ReplyCallback) extends IncomingMessage


  //
  // Messages from within the server
  //
  sealed trait OutgoingMessage extends Message with CborSerializable

  final case class ActivitySessionJoined(activityId: String, sessionId: String, state: Map[String, JValue]) extends OutgoingMessage

  final case class ActivitySessionLeft(activityId: String, sessionId: String) extends OutgoingMessage

  final case class ActivityStateUpdated(activityId: String,
                                  sessionId: String,
                                  state: Map[String, JValue],
                                  complete: Boolean,
                                  removed: List[String]) extends OutgoingMessage

}
