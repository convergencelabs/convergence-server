package com.convergencelabs.server.domain.activity

import akka.actor.ActorRef
import com.convergencelabs.server.domain.DomainFqn
import org.json4s.JsonAST.JValue

// Incoming Activity Messages
sealed trait IncomingActivityMessage {
  val domain: DomainFqn
  val activityId: String
}

case class ActivityParticipantsRequest(domain: DomainFqn, activityId: String) extends IncomingActivityMessage

case class ActivityJoinRequest(domain: DomainFqn, activityId: String, sessionId: String, state: Map[String, JValue], actorRef: ActorRef) extends IncomingActivityMessage
case class ActivityLeave(domain: DomainFqn, activityId: String, sessionId: String) extends IncomingActivityMessage

case class ActivitySetState(domain: DomainFqn, activityId: String, sessionId: String, state: Map[String, JValue]) extends IncomingActivityMessage
case class ActivityRemoveState(domain: DomainFqn, activityId: String, sessionId: String, keys: List[String]) extends IncomingActivityMessage
case class ActivityClearState(domain: DomainFqn, activityId: String, sessionId: String) extends IncomingActivityMessage

// Outgoing Activity Messages
sealed trait OutgoingActivityMessage {
}

case class ActivityJoinResponse(state: Map[String, Map[String, JValue]]) extends OutgoingActivityMessage
case class ActivityParticipants(state: Map[String, Map[String, JValue]]) extends OutgoingActivityMessage

case class ActivitySessionJoined(activityId: String, sessionId: String, state: Map[String, JValue]) extends OutgoingActivityMessage
case class ActivitySessionLeft(activityId: String, sessionId: String) extends OutgoingActivityMessage

case class ActivityRemoteStateSet(activityId: String, sessionId: String, state: Map[String, JValue]) extends OutgoingActivityMessage
case class ActivityRemoteStateRemoved(activityId: String, sessionId: String, keys: List[String]) extends OutgoingActivityMessage
case class ActivityRemoteStateCleared(activityId: String, sessionId: String) extends OutgoingActivityMessage

// Exceptions
case class ActivityAlreadyJoinedException(activityId: String) extends Exception(s"Activity '${activityId}' is already joined.")
case class ActivityNotJoinedException(activityId: String) extends Exception(s"Activity '${activityId}' is not joined.")
