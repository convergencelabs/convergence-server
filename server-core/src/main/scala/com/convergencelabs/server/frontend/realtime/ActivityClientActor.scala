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
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoin
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityLeave

object ActivityClientActor {
  def props(activityServiceActor: ActorRef, sk: SessionKey): Props =
    Props(new ActivityClientActor(activityServiceActor, sk))
}

class ActivityClientActor(activityServiceActor: ActorRef, sk: SessionKey) extends Actor with ActorLogging {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case MessageReceived(message) if message.isInstanceOf[IncomingActivityNormalMessage] =>
      onMessageReceived(message.asInstanceOf[IncomingActivityNormalMessage])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[IncomingActivityRequestMessage] =>
      onRequestReceived(message.asInstanceOf[IncomingActivityRequestMessage], replyPromise)

    case ActivitySessionJoined(activityId, sk) =>
      context.parent ! ActivitySessionJoinedMessage(activityId, sk.serialize())
    case ActivitySessionLeft(activityId, sk) =>
      context.parent ! ActivitySessionLeftMessage(activityId, sk.serialize())
    case ActivityRemoteStateSet(activityId, sk, key, value) =>
      context.parent ! ActivityRemoteStateSetMessage(activityId, sk.serialize(), key, value)
    case ActivityRemoteStateCleared(activityId, sk, key) =>
      context.parent ! ActivityRemoteStateClearedMessage(activityId, sk.serialize(), key)

    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: IncomingActivityNormalMessage): Unit = {
    message match {
      case join: ActivityJoinMessage => onActivityJoin(join)
      case leave: ActivityLeaveMessage => onActivityLeave(leave)
      case setState: ActivitySetStateMessage => onActivityStateSet(setState)
      case clearState: ActivityClearStateMessage => onActivityStateCleared(clearState)
    }
  }

  def onActivityStateSet(message: ActivitySetStateMessage): Unit = {
    val ActivitySetStateMessage(id, key, value) = message
    this.activityServiceActor ! ActivitySetState(id, sk, key, value)
  }

  def onActivityStateCleared(message: ActivityClearStateMessage): Unit = {
    val ActivityClearStateMessage(id, key) = message
    this.activityServiceActor ! ActivityClearState(id, sk, key)
  }

  def onRequestReceived(message: IncomingActivityRequestMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case participant: ActivityParticipantsRequestMessage => onParticipantsRequest(participant, replyCallback)
    }
  }

  def onParticipantsRequest(request: ActivityParticipantsRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityParticipantsRequestMessage(activityId) = request
    val future = this.activityServiceActor ? ActivityParticipantsRequest(activityId)

    future.mapResponse[ActivityParticipants] onComplete {
      case Success(ActivityParticipants(state)) =>
        cb.reply(ActivityParticipantsResponseMessage(state.map({
          case (k, v) => (k.serialize() -> v)
        })))
      case Failure(cause) =>
        cb.unexpectedError("could not get participants")
    }
  }

  def onActivityJoin(request: ActivityJoinMessage): Unit = {
    val ActivityJoinMessage(activityId) = request
    this.activityServiceActor ! ActivityJoin(activityId, sk, self)
  }

  def onActivityLeave(request: ActivityLeaveMessage): Unit = {
    val ActivityLeaveMessage(activityId) = request
    this.activityServiceActor ! ActivityLeave(activityId, sk)
  }
}
