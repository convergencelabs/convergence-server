package com.convergencelabs.server.domain

import com.convergencelabs.server.domain.model.SessionKey

import PresenceServiceActor._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import com.convergencelabs.server.util.SubscriptionMap
import akka.actor.Terminated

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
  case class UserPresenceAvailability(userId: String, available: Boolean)

  case class SubscribePresence(userId: String, client: ActorRef)
  case class UnsubscribePresence(userId: String, client: ActorRef)
}

class PresenceServiceActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  private var presences = Map[String, UserPresence]()
  
  // FIXME These are particularly problematic.  We are storing actor refs and userIds
  // in memory.  there is not a good way to persist this.
  private var subscriptions = SubscriptionMap[ActorRef, String]()
  private var clients = Map[ActorRef, String]()

  
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
    case Terminated(client) =>
      handleDeathwatch(client)
  }

  private[this] def getPresence(userIds: List[String]): Unit = {
    sender ! lookupPresence(userIds)
  }

  private[this] def userConnected(userId: String, client: ActorRef): Unit = {
    clients += (client -> userId)
    val result = this.presences.get(userId) match {
      case Some(presence) =>
        presence.copy(clients = presence.clients + client)
      case None =>
        UserPresence(userId, true, Map(), Set(client))
    }
    
    context.watch(client)

    this.presences += (userId -> result)
    sender ! result
  }

  private[this] def userDiconnected(userId: String, client: ActorRef): Unit = {
    clients -= client
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

  private[this] def publishState(userId: String, key: String, value: Any): Unit = {
    this.presences.get(userId) match {
      case Some(presence) =>
        this.presences += (userId -> presence.copy(state = presence.state + (key -> value)))
        this.broadcastToSubscribed(userId, UserPresencePublishState(userId, key, value))
      case None =>
      // TODO Error
    }
  }

  private[this] def clearState(userId: String, key: String): Unit = {
    this.presences.get(userId) match {
      case Some(presence) =>
        this.presences += (userId -> presence.copy(state = presence.state - key))
        this.broadcastToSubscribed(userId, UserPresenceClearState(userId, key))
      case None =>
      // TODO Error
    }
  }

  private[this] def subscribe(userIds: List[String], client: ActorRef): Unit = {
    val result = userIds.map { userId =>
      subscribe(userId, client)
    }
    lookupPresence(userIds)
  }

  private[this] def subscribe(userId: String, client: ActorRef): Unit = {
    this.subscriptions.subscribe(client, userId)
  }

  private[this] def unsubscribe(userId: String, client: ActorRef): Unit = {
    this.subscriptions.unsubscribe(client, userId)
  }

  private[this] def lookupPresence(userIds: List[String]): List[UserPresence] = {
    userIds.map { userId =>
      this.presences.get(userId) match {
        case Some(presence) =>
          presence
        case None =>
          UserPresence(userId, false, Map(), Set())
      }
    }
  }

  private[this] def broadcastToSubscribed(userId: String, message: Any): Unit = {
    this.subscriptions.subscribers(userId) foreach { client =>
      client ! message
    }
  }
  
  private[this] def handleDeathwatch(client: ActorRef): Unit = {
    this.subscriptions.unsubscribe(client)
  }
}
