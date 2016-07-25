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
    sender ! lookupPresence(userIds)
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
    this.presences.get(userId) match { 
      case Some(presence) =>
        this.presences += (userId -> presence.copy(state=presence.state +  (key -> value)))
        this.broadcastToSubscribed(userId, UserPresencePublishState(userId, key, value))
      case None =>
        // TODO Error
    }
  }

  def clearState(userId: String, key: String): Unit = {
    this.presences.get(userId) match { 
      case Some(presence) =>
        this.presences += (userId -> presence.copy(state=presence.state - key))
        this.broadcastToSubscribed(userId, UserPresenceClearState(userId, key))
      case None =>
        // TODO Error
    }
  }
  
  def subscribe(userIds: List[String], client: ActorRef): Unit = {
    val result = userIds.map { userId =>  
      subscribe(userId, client)
    }
    lookupPresence(userIds)
  }
  
  
  def subscribe(userId: String, client: ActorRef): Unit = {
    val newSubscribers = this.subscriptions.getOrElse(userId, Set()) + client
    this.subscriptions += (userId -> newSubscribers)
  }
  
  def unsubscribe(userId: String, client: ActorRef): Unit = {
    val newSubscribers = this.subscriptions.getOrElse(userId, Set()) - client
    if (newSubscribers.isEmpty) {
      
    } else {
      this.subscriptions += (userId -> newSubscribers)
    }
  }
  
  private [this] def lookupPresence(userIds: List[String]): List[UserPresence] = {
    userIds.map { userId =>
      this.presences.get(userId) match {
        case Some(presence) =>
          presence
        case None =>
          UserPresence(userId, false, Map(), Set())
      }
    }
  }
  
  private [this] def broadcastToSubscribed(userId: String, message: Any): Unit = {
    this.subscriptions.get(userId) map { _.foreach { client =>  
      client ! message
    }}
  }
}
