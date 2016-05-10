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
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityOpenRequest
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityOpenSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityCloseRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityCloseSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoinSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoinRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityLeaveSuccess
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityLeaveRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivitySetState
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityClearState

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
    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: IncomingActivityNormalMessage): Unit = {
    message match {
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
      case open: ActivityOpenRequestMessage => onActivityOpen(open, replyCallback)
      case close: ActivityCloseRequestMessage => onActivityClose(close, replyCallback)
      case join: ActivityJoinRequestMessage => onActivityJoin(join, replyCallback)
      case leave: ActivityLeaveRequestMessage => onActivityLeave(leave, replyCallback)
    }
  }

  def onActivityOpen(request: ActivityOpenRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityOpenRequestMessage(activityId) = request
    val future = this.activityServiceActor ? ActivityOpenRequest(activityId, sk, self)

    future.mapResponse[ActivityOpenSuccess] onComplete {
      case Success(ActivityOpenSuccess(state)) =>
        cb.reply(ActivityOpenSuccessMessage(state.map({
          case (k, v) => (k.serialize() -> v)
        })))
      case Failure(cause) =>
        cb.unexpectedError("could not open activity")
    }
  }

  def onActivityClose(request: ActivityCloseRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityCloseRequestMessage(activityId) = request
    val future = this.activityServiceActor ? ActivityCloseRequest(activityId, sk)

    future.mapResponse[ActivityCloseSuccess] onComplete {
      case Success(ActivityCloseSuccess()) =>
        cb.reply(ActivityCloseSuccessMessage())
      case Failure(cause) =>
        cb.unexpectedError("could not close activity")
    }
  }

  def onActivityJoin(request: ActivityJoinRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityJoinRequestMessage(activityId) = request
    val future = this.activityServiceActor ? ActivityJoinRequest(activityId, sk)

    future.mapResponse[ActivityJoinSuccess] onComplete {
      case Success(ActivityJoinSuccess()) =>
        cb.reply(ActivityJoinSuccessMessage())
      case Failure(cause) =>
        cb.unexpectedError("could not join activity")
    }
  }

  def onActivityLeave(request: ActivityLeaveRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityLeaveRequestMessage(activityId) = request
    val future = this.activityServiceActor ? ActivityLeaveRequest(activityId, sk)

    future.mapResponse[ActivityLeaveSuccess] onComplete {
      case Success(ActivityLeaveSuccess()) =>
        cb.reply(ActivityLeaveSuccessMessage())
      case Failure(cause) =>
        cb.unexpectedError("could not leave activity")
    }
  }
}
