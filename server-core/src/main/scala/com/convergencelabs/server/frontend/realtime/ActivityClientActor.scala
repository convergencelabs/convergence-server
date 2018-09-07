package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import com.convergencelabs.server.util.concurrent.AskFuture
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySetState
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityClearState
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySessionJoined
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySessionLeft
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityRemoteStateSet
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityRemoteStateCleared
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityParticipantsRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityParticipants
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoinRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityLeave
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityRemoteStateRemoved
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityRemoveState
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoinResponse
import convergence.protocol.Incoming
import convergence.protocol.Activity
import convergence.protocol.Request
import convergence.protocol.activity.ActivitySessionJoinedMessage
import convergence.protocol.activity.ActivitySessionLeftMessage
import convergence.protocol.activity.ActivityRemoteStateSetMessage
import convergence.protocol.activity.ActivityRemoteStateRemovedMessage
import convergence.protocol.activity.ActivityRemoteStateClearedMessage
import convergence.protocol.activity.ActivityLeaveMessage
import convergence.protocol.activity.ActivitySetStateMessage
import convergence.protocol.activity.ActivityRemoveStateMessage
import convergence.protocol.activity.ActivityClearStateMessage
import convergence.protocol.activity.ActivityJoinMessage
import convergence.protocol.activity.ActivityParticipantsRequestMessage
import convergence.protocol.activity.ActivityParticipantsResponseMessage
import convergence.protocol.activity.ActivityState
import convergence.protocol.activity.ActivityJoinResponseMessage

object ActivityClientActor {
  def props(activityServiceActor: ActorRef, sk: SessionKey): Props =
    Props(new ActivityClientActor(activityServiceActor, sk))
}

class ActivityClientActor(activityServiceActor: ActorRef, sk: SessionKey) extends Actor with ActorLogging {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case MessageReceived(message) if message.isInstanceOf[Incoming with Activity] =>
      onMessageReceived(message.asInstanceOf[Incoming with Activity])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[Request with Activity] =>
      onRequestReceived(message.asInstanceOf[Request with Activity], replyPromise)

    case ActivitySessionJoined(activityId, SessionKey(uid, sid, admin), state) =>
      context.parent ! ActivitySessionJoinedMessage(activityId, 
          Some(convergence.protocol.authentication.SessionKey(uid, sid)), state)
    case ActivitySessionLeft(activityId, SessionKey(uid, sid, admin)) =>
      context.parent ! ActivitySessionLeftMessage(activityId, 
          Some(convergence.protocol.authentication.SessionKey(uid, sid)))
    case ActivityRemoteStateSet(activityId, SessionKey(uid, sid, admin), state) =>
      context.parent ! ActivityRemoteStateSetMessage(activityId, 
          Some(convergence.protocol.authentication.SessionKey(uid, sid)), state)
    case ActivityRemoteStateRemoved(activityId, SessionKey(uid, sid, admin), keys) =>
      context.parent ! ActivityRemoteStateRemovedMessage(activityId, 
          Some(convergence.protocol.authentication.SessionKey(uid, sid)), keys)
    case ActivityRemoteStateCleared(activityId, SessionKey(uid, sid, admin)) =>
      context.parent ! ActivityRemoteStateClearedMessage(activityId, 
          Some(convergence.protocol.authentication.SessionKey(uid, sid)))

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
    this.activityServiceActor ! ActivitySetState(id, sk, state)
  }

  def onActivityStateRemoved(message: ActivityRemoveStateMessage): Unit = {
    val ActivityRemoveStateMessage(id, keys) = message
    this.activityServiceActor ! ActivityRemoveState(id, sk, keys.toList)
  }

  def onActivityStateCleared(message: ActivityClearStateMessage): Unit = {
    val ActivityClearStateMessage(id) = message
    this.activityServiceActor ! ActivityClearState(id, sk)
  }

  def onRequestReceived(message: Request with Activity, replyCallback: ReplyCallback): Unit = {
    message match {
      case join: ActivityJoinMessage                       => onActivityJoin(join, replyCallback)
      case participant: ActivityParticipantsRequestMessage => onParticipantsRequest(participant, replyCallback)
    }
  }

  def onParticipantsRequest(request: ActivityParticipantsRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityParticipantsRequestMessage(activityId) = request
    val future = this.activityServiceActor ? ActivityParticipantsRequest(activityId)

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
    val future = this.activityServiceActor ? ActivityJoinRequest(activityId, sk, state, self)

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
    this.activityServiceActor ! ActivityLeave(activityId, sk)
  }
}
