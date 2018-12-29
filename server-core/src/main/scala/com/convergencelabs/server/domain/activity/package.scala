package com.convergencelabs.server.domain.activity

import com.convergencelabs.server.domain.model.SessionKey
import akka.actor.ActorRef
import com.convergencelabs.server.domain.DomainFqn

// Incoming Activity Messages
sealed trait IncomingActivityMessage {
  val domain: DomainFqn
  val activityId: String
}

case class ActivityParticipantsRequest(domain: DomainFqn, activityId: String) extends IncomingActivityMessage
case class ActivityJoinRequest(domain: DomainFqn, activityId: String, sk: SessionKey, state: Map[String, String], actorRef: ActorRef) extends IncomingActivityMessage
case class ActivityLeave(domain: DomainFqn, activityId: String, sk: SessionKey) extends IncomingActivityMessage

case class ActivitySetState(domain: DomainFqn, activityId: String, sk: SessionKey, state: Map[String, String]) extends IncomingActivityMessage
case class ActivityRemoveState(domain: DomainFqn, activityId: String, sk: SessionKey, keys: List[String]) extends IncomingActivityMessage
case class ActivityClearState(domain: DomainFqn, activityId: String, sk: SessionKey) extends IncomingActivityMessage

// Outgoing Activity Messages
sealed trait OutgoingActivityMessage {
}

case class ActivityJoinResponse(state: Map[SessionKey, Map[String, String]]) extends OutgoingActivityMessage
case class ActivityParticipants(state: Map[SessionKey, Map[String, String]]) extends OutgoingActivityMessage

case class ActivitySessionJoined(activityId: String, sk: SessionKey, state: Map[String, String]) extends OutgoingActivityMessage
case class ActivitySessionLeft(activityId: String, sk: SessionKey) extends OutgoingActivityMessage

case class ActivityRemoteStateSet(activityId: String, sk: SessionKey, state: Map[String, String]) extends OutgoingActivityMessage
case class ActivityRemoteStateRemoved(activityId: String, sk: SessionKey, keys: List[String]) extends OutgoingActivityMessage
case class ActivityRemoteStateCleared(activityId: String, sk: SessionKey) extends OutgoingActivityMessage

// Exceptions
case class ActivityAlreadyJoinedException(activityId: String) extends Exception(s"Activity '${activityId}' is already joined.")
case class ActivityNotJoinedException(activityId: String) extends Exception(s"Activity '${activityId}' is not joined.")
