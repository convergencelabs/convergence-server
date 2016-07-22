package com.convergencelabs.server.domain

import com.convergencelabs.server.domain.model.SessionKey

import PresenceServiceActor._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props

object PresenceServiceActor {

  val RelativePath = "presenceService"

  def props(domainFqn: DomainFqn): Props = Props(
    new PresenceServiceActor(domainFqn))

  case class PresenceRequest(userIds: List[String])
  case class UserPresence(userId: String, avaialble: Boolean, state: Map[String, Any], clients: Set[ActorRef])

  case class UserConnectionRequest(userId: String, client: ActorRef)
  case class UserConnectionResponse(state: Map[String, Any])

  case class UserDisconnectionRequest(userId: String, client: ActorRef)

  case class UserPresencePublishState(userId: String, key: String, value: Any)
  case class UserPresenceClearState(userId: String, key: String)

  case class SubscribePresence(userId: String, client: ActorRef)
  case class UnsubscribePresence(userId: String, client: ActorRef)
}

class PresenceServiceActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  private var presences = Map[String, UserPresence]()
  private var subscriptions = Map[String, Set[ActorRef]]()

  def receive: Receive = {
    case PresenceRequest(userIds) =>
      getPresence(userIds)
    case UserConnectionRequest(userId, client) =>
      userConnected(userId, client)
    case UserDisconnectionRequest(userId, client) =>
      userDiconnected(userId, client)
    case UserPresencePublishState(userId, key, value) =>
      publishState(userId, key, value)
    case UserPresenceClearState(userId, key) =>
      clearState(userId, key)
    case SubscribePresence(userId, client) =>
      subscribe(userId, client)
    case UnsubscribePresence(userId, client) =>
      unsubscribe(userId, client)
  }

  def getPresence(userIds: List[String]): Unit = {
    val result = userIds.map { userId =>
      this.presences.get(userId) match {
        case Some(presence) =>
          presence
        case None =>
          UserPresence(userId, false, Map(), Set())
      }
    }
    sender ! result
  }

  def userConnected(userId: String, client: ActorRef): Unit = {
    val result = this.presences.get(userId) match {
      case Some(presence) =>
        presence.copy(clients = presence.clients + client)
      case None =>
        UserPresence(userId, true, Map(), Set(client))
    }

    this.presences += (userId -> result)
    sender ! result
  }

  def userDiconnected(userId: String, client: ActorRef): Unit = {
    this.presences.get(userId) match {
      case Some(presence) =>
        val updated = presence.copy(clients = presence.clients - client)
        if (updated.clients.isEmpty) {
          this.presences -= userId
        } else {
          this.presences += (userId -> updated)
        }
      case None =>
    }
  }

  def publishState(userId: String, key: String, value: Any): Unit = {
     // Basically look up the presense, set the state, then look at the subscriptions map to see
    // if anyone else is subscribed at this user id, and send a message to those clients.
  }

  def clearState(userId: String, key: String): Unit = {
 // Basically look up the presense, clear the state, then look at the subscriptions map to see
    // if anyone else is subscribed at this user id, and send a message to those clients.
  }
  
  def subscribe(userId: String, client: ActorRef): Unit = {
    // Add this actor ref to the SET that is in the subscriptions map at the value for the key that
    // is this user id.  Create the key/value pair if it is not there.
  }
  
  def unsubscribe(userId: String, client: ActorRef): Unit = {
    // remove this actor ref from the SET that is in the subscriptions map at the value for the key that
    // is this user id.  remove the key/value pair if it is now empty.
  }
}
