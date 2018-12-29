package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.domain.activity.ActivityActorSharding
import com.convergencelabs.server.domain.activity.ActivityClearState
import com.convergencelabs.server.domain.activity.ActivityJoinRequest
import com.convergencelabs.server.domain.activity.ActivityJoinResponse
import com.convergencelabs.server.domain.activity.ActivityLeave
import com.convergencelabs.server.domain.activity.ActivityParticipants
import com.convergencelabs.server.domain.activity.ActivityParticipantsRequest
import com.convergencelabs.server.domain.activity.ActivityRemoteStateCleared
import com.convergencelabs.server.domain.activity.ActivityRemoteStateRemoved
import com.convergencelabs.server.domain.activity.ActivityRemoteStateSet
import com.convergencelabs.server.domain.activity.ActivityRemoveState
import com.convergencelabs.server.domain.activity.ActivitySessionJoined
import com.convergencelabs.server.domain.activity.ActivitySessionLeft
import com.convergencelabs.server.domain.activity.ActivitySetState
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.util.concurrent.AskFuture
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.util.Timeout

import io.convergence.proto.Incoming
import io.convergence.proto.Activity
import io.convergence.proto.Request
import io.convergence.proto.activity.ActivitySessionJoinedMessage
import io.convergence.proto.activity.ActivitySessionLeftMessage
import io.convergence.proto.activity.ActivityRemoteStateSetMessage
import io.convergence.proto.activity.ActivityRemoteStateRemovedMessage
import io.convergence.proto.activity.ActivityRemoteStateClearedMessage
import io.convergence.proto.activity.ActivityLeaveMessage
import io.convergence.proto.activity.ActivitySetStateMessage
import io.convergence.proto.activity.ActivityRemoveStateMessage
import io.convergence.proto.activity.ActivityClearStateMessage
import io.convergence.proto.activity.ActivityJoinMessage
import io.convergence.proto.activity.ActivityParticipantsRequestMessage
import io.convergence.proto.activity.ActivityParticipantsResponseMessage
import io.convergence.proto.activity.ActivityState
import io.convergence.proto.activity.ActivityJoinResponseMessage

object ActivityClientActor {
  def props(activityServiceActor: ActorRef, domain: DomainFqn, sk: SessionKey): Props =
    Props(new ActivityClientActor(activityServiceActor, domain, sk))
}

class ActivityClientActor(activityServiceActor: ActorRef, domain: DomainFqn, sk: SessionKey) extends Actor with ActorLogging {
  import akka.pattern.ask
  
  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case MessageReceived(message) if message.isInstanceOf[Incoming with Activity] =>
      onMessageReceived(message.asInstanceOf[Incoming with Activity])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[Request with Activity] =>
      onRequestReceived(message.asInstanceOf[Request with Activity], replyPromise)

    case ActivitySessionJoined(activityId, SessionKey(uid, sid, admin), state) =>
      context.parent ! ActivitySessionJoinedMessage(activityId, 
          Some(io.convergence.proto.authentication.SessionKey(uid, sid)), state)
    case ActivitySessionLeft(activityId, SessionKey(uid, sid, admin)) =>
      context.parent ! ActivitySessionLeftMessage(activityId, 
          Some(io.convergence.proto.authentication.SessionKey(uid, sid)))
    case ActivityRemoteStateSet(activityId, SessionKey(uid, sid, admin), state) =>
      context.parent ! ActivityRemoteStateSetMessage(activityId, 
          Some(io.convergence.proto.authentication.SessionKey(uid, sid)), state)
    case ActivityRemoteStateRemoved(activityId, SessionKey(uid, sid, admin), keys) =>
      context.parent ! ActivityRemoteStateRemovedMessage(activityId, 
          Some(io.convergence.proto.authentication.SessionKey(uid, sid)), keys)
    case ActivityRemoteStateCleared(activityId, SessionKey(uid, sid, admin)) =>
      context.parent ! ActivityRemoteStateClearedMessage(activityId, 
          Some(io.convergence.proto.authentication.SessionKey(uid, sid)))

    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: Incoming with Activity): Unit = {
    message match {
      case leave: ActivityLeaveMessage             => onActivityLeave(leave)
      case setState: ActivitySetStateMessage       => onActivityStateSet(setState)
      case removeState: ActivityRemoveStateMessage => onActivityStateRemoved(removeState)
      case clearState: ActivityClearStateMessage   => onActivityStateCleared(clearState)
    }
  }

  def onActivityStateSet(message: ActivitySetStateMessage): Unit = {
    val ActivitySetStateMessage(id, state) = message
    this.activityServiceActor ! ActivitySetState(domain, id, sk, state)
  }

  def onActivityStateRemoved(message: ActivityRemoveStateMessage): Unit = {
    val ActivityRemoveStateMessage(id, keys) = message
    this.activityServiceActor ! ActivityRemoveState(domain, id, sk, keys.toList)
  }

  def onActivityStateCleared(message: ActivityClearStateMessage): Unit = {
    val ActivityClearStateMessage(id) = message
    this.activityServiceActor ! ActivityClearState(domain, id, sk)
  }

  def onRequestReceived(message: Request with Activity, replyCallback: ReplyCallback): Unit = {
    message match {
      case join: ActivityJoinMessage                       => onActivityJoin(join, replyCallback)
      case participant: ActivityParticipantsRequestMessage => onParticipantsRequest(participant, replyCallback)
    }
  }

  def onParticipantsRequest(request: ActivityParticipantsRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityParticipantsRequestMessage(activityId) = request
    val future = this.activityServiceActor ? ActivityParticipantsRequest(domain, activityId)

    future.mapResponse[ActivityParticipants] onComplete {
      case Success(ActivityParticipants(state)) =>
        cb.reply(ActivityParticipantsResponseMessage(state.map({
          case (k, v) => (k.serialize() -> ActivityState(v))
        })))
      case Failure(cause) =>
        cb.unexpectedError("could not get participants")
    }
  }

  def onActivityJoin(request: ActivityJoinMessage, cb: ReplyCallback): Unit = {
    val ActivityJoinMessage(activityId, state) = request
    val future = this.activityServiceActor ? ActivityJoinRequest(domain, activityId, sk, state, self)

    future.mapResponse[ActivityJoinResponse] onComplete {
      case Success(ActivityJoinResponse(state)) =>
        cb.reply(ActivityJoinResponseMessage(state.map({
          case (k, v) => (k.serialize() -> ActivityState(v))
        })))
      case Failure(cause) =>
        cb.unexpectedError("could not join activity")
    }
  }

  def onActivityLeave(request: ActivityLeaveMessage): Unit = {
    val ActivityLeaveMessage(activityId) = request
    this.activityServiceActor ! ActivityLeave(domain, activityId, sk)
  }
}
