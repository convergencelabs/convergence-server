package com.convergencelabs.server.domain

import com.convergencelabs.server.domain.model.SessionKey

import ActivityServiceActor.ActivityClearState
import ActivityServiceActor.ActivitySetState
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityShutdownRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityParticipants
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityParticipantsRequest
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityLeave
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoinResponse
import com.convergencelabs.server.domain.ActivityServiceActor.ActivityJoinRequest

object ActivityServiceActor {

  val RelativePath = "activityService"

  def props(domainFqn: DomainFqn): Props = Props(
    new ActivityServiceActor(domainFqn))

  // Incoming Messages
  case class ActivityParticipantsRequest(activityId: String)
  case class ActivityJoinRequest(activityId: String, sk: SessionKey, state: Map[String, Any], actorRef: ActorRef)
  case class ActivityLeave(activityId: String, sk: SessionKey)

  case class ActivitySetState(activityId: String, sk: SessionKey, state: Map[String, Any])
  case class ActivityRemoveState(activityId: String, sk: SessionKey, keys: List[String])
  case class ActivityClearState(activityId: String, sk: SessionKey)

  // Outgoing Messages
  case class ActivityJoinResponse(state: Map[SessionKey, Map[String, Any]])
  case class ActivityParticipants(state: Map[SessionKey, Map[String, Any]])

  case class ActivitySessionJoined(activityId: String, sk: SessionKey, state: Map[String, Any])
  case class ActivitySessionLeft(activityId: String, sk: SessionKey)

  case class ActivityRemoteStateSet(activityId: String, sk: SessionKey, state: Map[String, Any])
  case class ActivityRemoteStateRemoved(activityId: String, sk: SessionKey, keys: List[String])
  case class ActivityRemoteStateCleared(activityId: String, sk: SessionKey)

  case class ActivityShutdownRequest(activityId: String)
}

// TODO we could forward the activity actor back to the client at some point so message
// don't have to bottleneck here.
class ActivityServiceActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  private[this] var openActivities = Map[String, ActorRef]()

  def receive: Receive = {
    case joinRequest: ActivityJoinRequest => getAndForward(joinRequest.activityId, joinRequest)
    case leaveRequest: ActivityLeave => getAndForward(leaveRequest.activityId, leaveRequest)
    case participantsRequest: ActivityParticipantsRequest => participantRequest(participantsRequest.activityId, participantsRequest)
    case setState: ActivitySetState => getAndForward(setState.activityId, setState)
    case removeState: ActivitySetState => getAndForward(removeState.activityId, removeState)
    case clearState: ActivityClearState => getAndForward(clearState.activityId, clearState)
    case shutdown: ActivityShutdownRequest => handleShutdownRequest(shutdown)
  }
  
  private[this] def participantRequest(activityId: String, message: ActivityParticipantsRequest): Unit = {
    if(!openActivities.contains(activityId)) {
      sender ! ActivityParticipants(new ActivityStateMap().getState())
    } else {
      getAndForward(activityId, message);
    }
  }

  private[this] def getAndForward(activityId: String, message: Any): Unit = {
    if (!openActivities.contains(activityId)) {
      // not yet open.  Create the actor.
      openActivities += (activityId -> context.actorOf(ActivityActor.props(activityId)))
    }
    
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
