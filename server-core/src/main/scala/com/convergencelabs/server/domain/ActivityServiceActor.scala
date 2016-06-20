package com.convergencelabs.server.domain

import com.convergencelabs.server.domain.model.SessionKey

import ActivityServiceActor.ActivityClearState
import ActivityServiceActor.ActivityCloseRequest
import ActivityServiceActor.ActivityJoinRequest
import ActivityServiceActor.ActivityLeaveRequest
import ActivityServiceActor.ActivityOpenRequest
import ActivityServiceActor.ActivitySetState
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityShutdownRequest

object ActivityServiceActor {

  val RelativePath = "activityService"

  def props(domainFqn: DomainFqn): Props = Props(
    new ActivityServiceActor(domainFqn))

  // Incoming Messages
  case class ActivityOpenRequest(activityId: String, sk: SessionKey, actorRef: ActorRef)
  case class ActivityCloseRequest(activityId: String, sk: SessionKey)
  case class ActivityJoinRequest(activityId: String, sk: SessionKey)
  case class ActivityLeaveRequest(activityId: String, sk: SessionKey)

  case class ActivitySetState(activityId: String, sk: SessionKey, key: String, value: Any)
  case class ActivityClearState(activityId: String, sk: SessionKey, key: String)

  // Outgoing Messages
  case class ActivityOpenSuccess(state: Map[SessionKey, Map[String, Any]])
  case class ActivityCloseSuccess()
  case class ActivityJoinSuccess()
  case class ActivityLeaveSuccess()

  case class ActivitySessionJoined(activityId: String, sk: SessionKey)
  case class ActivitySessionLeft(activityId: String, sk: SessionKey)

  case class ActivityRemoteStateSet(activityId: String, sk: SessionKey, key: String, value: Any)
  case class ActivityRemoteStateCleared(activityId: String, sk: SessionKey, key: String)

  case class ActivityShutdownRequest(activityId: String)
}

// TODO we could forward the activity actor back to the client at some point so message
// don't have to bottleneck here.
class ActivityServiceActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  private[this] var openActivities = Map[String, ActorRef]()

  def receive: Receive = {
    case openRequest: ActivityOpenRequest => open(openRequest)
    case closeRequest: ActivityCloseRequest => getAndForward(closeRequest.activityId, closeRequest)
    case joinRequest: ActivityJoinRequest => getAndForward(joinRequest.activityId, joinRequest)
    case leaveRequest: ActivityLeaveRequest => getAndForward(leaveRequest.activityId, leaveRequest)
    case setState: ActivitySetState => getAndForward(setState.activityId, setState)
    case clearState: ActivityClearState => getAndForward(clearState.activityId, clearState)
    case shutdown: ActivityShutdownRequest => handleShutdownRequest(shutdown)
  }

  private[this] def open(openRequest: ActivityOpenRequest): Unit = {
    if (!openActivities.contains(openRequest.activityId)) {
      // not yet open.  Create the actor.
      openActivities += (openRequest.activityId -> context.actorOf(
        ActivityActor.props(openRequest.activityId)))
    }

    val activity = openActivities(openRequest.activityId)
    activity forward openRequest
  }

  private[this] def getAndForward(activityId: String, message: Any): Unit = {
    openActivities.get(activityId) match {
      case Some(activity) =>
        activity forward message
      case None =>
        sender ! new akka.actor.Status.Failure(new IllegalStateException("No such open activity exists."))
    }
  }

  private[this] def handleShutdownRequest(shutdown: ActivityShutdownRequest): Unit = {
    log.debug(s"Shutdown request for activity: ${shutdown.activityId}")
    // FIXME there is a race condition here.
    openActivities.get(shutdown.activityId) match {
      case Some(activity) =>
        openActivities -= shutdown.activityId
        context.stop(activity)
      case None =>
        log.error("Request to shutdown an actor that is not open");
    }
  }
}
