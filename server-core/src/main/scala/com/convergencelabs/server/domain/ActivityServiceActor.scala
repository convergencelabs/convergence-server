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
}

// TODO if we need to, each activity could be modeled by an individual actor
// which would somewhat improve parallelism.  We could send the "open" to this
// actor but then create / forward to another actor and send that actors ref
// back to the client.
class ActivityServiceActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  private[this] val activityManager = new ActivityManager();

  def receive: Receive = {
    case ActivityOpenRequest(id, sk, client) => activityManager.open(id, sk, client, sender)
    case ActivityCloseRequest(id, sk) => activityManager.close(id, sk, sender)
    case ActivityJoinRequest(id, sk) => activityManager.join(id, sk, sender)
    case ActivityLeaveRequest(id, sk) => activityManager.leave(id, sk, sender)
    case ActivitySetState(id, sk, key, value) => activityManager.setState(id, sk, key, value)
    case ActivityClearState(id, sk, key) => activityManager.clearState(id, sk, key)
  }
}
