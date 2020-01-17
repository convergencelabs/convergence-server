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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.activity._
import com.convergencelabs.convergence.server.domain.activity._
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserSessionId}
import com.convergencelabs.convergence.server.util.concurrent.AskFuture

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

object ActivityClientActor {
  def props(activityServiceActor: ActorRef, domain: DomainId, session: DomainUserSessionId): Props =
    Props(new ActivityClientActor(activityServiceActor, domain, session))
}

class ActivityClientActor(activityServiceActor: ActorRef, domain: DomainId, session: DomainUserSessionId) extends Actor with ActorLogging {
  import akka.pattern.ask

  private[this] implicit val timeout: Timeout = Timeout(5 seconds)
  private[this] implicit val ec: ExecutionContextExecutor = context.dispatcher

  def receive: Receive = {
    // Incoming messages
    case MessageReceived(message) if message.isInstanceOf[NormalMessage with ActivityMessage] =>
      onMessageReceived(message.asInstanceOf[NormalMessage with ActivityMessage])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[RequestMessage with ActivityMessage] =>
      onRequestReceived(message.asInstanceOf[RequestMessage with ActivityMessage], replyPromise)

    // Outgoing messages
    case ActivitySessionJoined(activityId, sessionId, state) =>
      context.parent ! ActivitySessionJoinedMessage(activityId, sessionId, JsonProtoConverter.jValueMapToValueMap(state))
    case ActivitySessionLeft(activityId, sessionId) =>
      context.parent ! ActivitySessionLeftMessage(activityId, sessionId)
    case ActivityStateUpdated(activityId, sessionId, state, complete, removed) =>
      context.parent ! ActivityStateUpdatedMessage(
          activityId, sessionId, JsonProtoConverter.jValueMapToValueMap(state), complete, removed)
      
    // Everything else
    case x: Any =>
      log.warning("Unexpected activity message: {}", x)
  }
  

  //
  // Incoming Messages
  //

  def onMessageReceived(message: NormalMessage with ActivityMessage): Unit = {
    message match {
      case leave: ActivityLeaveMessage => 
        onActivityLeave(leave)
      case setState: ActivityUpdateStateMessage => 
        onActivityUpdateState(setState)
    }
  }

  def onActivityUpdateState(message: ActivityUpdateStateMessage): Unit = {
    val ActivityUpdateStateMessage(id, state, complete, removed) = message
    this.activityServiceActor ! ActivityUpdateState(
        domain, id, session.sessionId, JsonProtoConverter.valueMapToJValueMap(state), complete, removed.toList)
  }


  def onRequestReceived(message: RequestMessage with ActivityMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case join: ActivityJoinRequestMessage =>
        onActivityJoin(join, replyCallback)
      case participant: ActivityParticipantsRequestMessage =>
        onParticipantsRequest(participant, replyCallback)
    }
  }

  def onParticipantsRequest(RequestMessage: ActivityParticipantsRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityParticipantsRequestMessage(activityId) = RequestMessage
    val future = this.activityServiceActor ? ActivityParticipantsRequest(domain, activityId)

    future.mapResponse[ActivityParticipants] onComplete {
      case Success(ActivityParticipants(state)) =>
        cb.reply(ActivityParticipantsResponseMessage(state.map {
          case (k, v) => k -> ActivityStateData(JsonProtoConverter.jValueMapToValueMap(v))
        }))
      case Failure(cause) =>
        val message = s"could not get participants for activity $activityId"
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  def onActivityJoin(RequestMessage: ActivityJoinRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityJoinRequestMessage(activityId, state) = RequestMessage
    val message = ActivityJoinRequest(domain, activityId, session.sessionId, JsonProtoConverter.valueMapToJValueMap(state), self)
    val future = this.activityServiceActor ? message

    future.mapResponse[ActivityJoinResponse] onComplete {
      case Success(ActivityJoinResponse(state)) =>
        cb.reply(ActivityJoinResponseMessage(state.map {
          case (k, v) => k -> ActivityStateData(JsonProtoConverter.jValueMapToValueMap(v))
        }))
      case Failure(cause) =>
        val message = s"Could not join activity '$activityId'"
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  def onActivityLeave(RequestMessage: ActivityLeaveMessage): Unit = {
    val ActivityLeaveMessage(activityId) = RequestMessage
    this.activityServiceActor ! ActivityLeave(domain, activityId, session.sessionId)
  }
}
