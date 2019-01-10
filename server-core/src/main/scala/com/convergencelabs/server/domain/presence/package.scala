package com.convergencelabs.server.domain.presence

import akka.actor.ActorRef
import org.json4s.JsonAST.JValue

case class PresenceRequest(usernames: List[String])
case class UserPresence(username: String, available: Boolean, state: Map[String, JValue], clients: Set[ActorRef])

case class UserConnected(username: String, client: ActorRef)

case class UserPresenceSetState(username: String, state: Map[String, JValue])
case class UserPresenceRemoveState(username: String, keys: List[String])
case class UserPresenceClearState(username: String)
case class UserPresenceAvailability(username: String, available: Boolean)

case class SubscribePresence(usernames: List[String], client: ActorRef)
case class UnsubscribePresence(username: List[String], client: ActorRef)