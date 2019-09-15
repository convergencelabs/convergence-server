package com.convergencelabs.server.api.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.domain.activity.ActivityActorSharding
import com.convergencelabs.server.domain.activity.ActivityJoinRequest
import com.convergencelabs.server.domain.activity.ActivityJoinResponse
import com.convergencelabs.server.domain.activity.ActivityLeave
import com.convergencelabs.server.domain.activity.ActivityParticipants
import com.convergencelabs.server.domain.activity.ActivityParticipantsRequest
import com.convergencelabs.server.domain.activity.ActivityUpdateState
import com.convergencelabs.server.domain.activity.ActivityStateUpdated
import com.convergencelabs.server.domain.activity.ActivitySessionJoined
import com.convergencelabs.server.domain.activity.ActivitySessionLeft
import com.convergencelabs.server.util.concurrent.AskFuture
import com.convergencelabs.server.domain.DomainId

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.util.Timeout

import io.convergence.proto.Normal
import io.convergence.proto.Activity
import io.convergence.proto.Request
import io.convergence.proto.activity.ActivitySessionJoinedMessage
import io.convergence.proto.activity.ActivitySessionLeftMessage
import io.convergence.proto.activity.ActivityLeaveMessage
import io.convergence.proto.activity.ActivityJoinRequestMessage
import io.convergence.proto.activity.ActivityParticipantsRequestMessage
import io.convergence.proto.activity.ActivityParticipantsResponseMessage
import io.convergence.proto.activity.ActivityState
import io.convergence.proto.activity.ActivityStateUpdatedMessage
import io.convergence.proto.activity.ActivityJoinResponseMessage
import io.convergence.proto.activity.ActivityUpdateStateMessage
import com.convergencelabs.server.domain.DomainUserSessionId



object ActivityClientActor {
  def props(activityServiceActor: ActorRef, domain: DomainId, session: DomainUserSessionId): Props =
    Props(new ActivityClientActor(activityServiceActor, domain, session))
}

class ActivityClientActor(activityServiceActor: ActorRef, domain: DomainId, session: DomainUserSessionId) extends Actor with ActorLogging {
  import akka.pattern.ask

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    // Incoming messages
    case MessageReceived(message) if message.isInstanceOf[Normal with Activity] =>
      onMessageReceived(message.asInstanceOf[Normal with Activity])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[Request with Activity] =>
      onRequestReceived(message.asInstanceOf[Request with Activity], replyPromise)

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

  def onMessageReceived(message: Normal with Activity): Unit = {
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


  def onRequestReceived(message: Request with Activity, replyCallback: ReplyCallback): Unit = {
    message match {
      case join: ActivityJoinRequestMessage =>
        onActivityJoin(join, replyCallback)
      case participant: ActivityParticipantsRequestMessage =>
        onParticipantsRequest(participant, replyCallback)
    }
  }

  def onParticipantsRequest(request: ActivityParticipantsRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityParticipantsRequestMessage(activityId) = request
    val future = this.activityServiceActor ? ActivityParticipantsRequest(domain, activityId)

    future.mapResponse[ActivityParticipants] onComplete {
      case Success(ActivityParticipants(state)) =>
        cb.reply(ActivityParticipantsResponseMessage(state.map {
          case (k, v) => (k -> ActivityState(JsonProtoConverter.jValueMapToValueMap(v)))
        }))
      case Failure(cause) =>
        cb.unexpectedError("could not get participants")
    }
  }

  def onActivityJoin(request: ActivityJoinRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityJoinRequestMessage(activityId, state) = request
    val message = ActivityJoinRequest(domain, activityId, session.sessionId, JsonProtoConverter.valueMapToJValueMap(state), self)
    val future = this.activityServiceActor ? message

    future.mapResponse[ActivityJoinResponse] onComplete {
      case Success(ActivityJoinResponse(state)) =>
        cb.reply(ActivityJoinResponseMessage(state.map {
          case (k, v) => (k -> ActivityState(JsonProtoConverter.jValueMapToValueMap(v)))
        }))
      case Failure(cause) =>
        cb.unexpectedError("could not join activity")
    }
  }

  def onActivityLeave(request: ActivityLeaveMessage): Unit = {
    val ActivityLeaveMessage(activityId) = request
    this.activityServiceActor ! ActivityLeave(domain, activityId, session.sessionId)
  }
}
