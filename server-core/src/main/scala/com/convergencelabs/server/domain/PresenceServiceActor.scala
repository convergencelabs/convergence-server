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

  case class PresenceRequest(usernames: List[String])
  case class UserPresence(username: String, available: Boolean, state: Map[String, Any], clients: Set[ActorRef])

  case class UserConnected(username: String, client: ActorRef)
  
  case class UserPresencePublishState(username: String, key: String, value: Any)
  case class UserPresenceClearState(username: String, key: String)
  case class UserPresenceAvailability(username: String, available: Boolean)

  case class SubscribePresence(username: String, client: ActorRef)
  case class UnsubscribePresence(username: String, client: ActorRef)
}

class PresenceServiceActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  private var presences = Map[String, UserPresence]()
  
  // FIXME These are particularly problematic.  We are storing actor refs and usernames
  // in memory.  there is not a good way to persist this.
  private var subscriptions = SubscriptionMap[ActorRef, String]()
  private var clients = Map[ActorRef, String]()

  
  def receive: Receive = {
    case PresenceRequest(usernames) =>
      getPresence(usernames)
    case UserConnected(username, client) =>
      userConnected(username, client)
    case UserPresencePublishState(username, key, value) =>
      publishState(username, key, value)
    case UserPresenceClearState(username, key) =>
      clearState(username, key)
    case SubscribePresence(username, client) =>
      subscribe(username, client)
    case UnsubscribePresence(username, client) =>
      unsubscribe(username, client)
    case Terminated(client) =>
      handleDeathwatch(client)
  }

  private[this] def getPresence(usernames: List[String]): Unit = {
    sender ! lookupPresence(usernames)
  }

  private[this] def userConnected(username: String, client: ActorRef): Unit = {
    clients += (client -> username)
    val result = this.presences.get(username) match {
      case Some(presence) =>
        presence.copy(clients = presence.clients + client)
      case None => {
        this.broadcastToSubscribed(username, UserPresenceAvailability(username, true))
        UserPresence(username, true, Map(), Set(client))
      }
    }
    
    context.watch(client)
    this.presences += (username -> result)
  }

  private[this] def userDiconnected(client: ActorRef): Unit = {
    clients.get(client) match {
      case Some(username) => {
        clients -= client
        this.presences.get(username) match {
          case Some(presence) =>
            val updated = presence.copy(clients = presence.clients - client)
            if (updated.clients.isEmpty) {
              this.presences -= username
              this.broadcastToSubscribed(username, UserPresenceAvailability(username, false))
            } else {
              this.presences += (username -> updated)
            }
          case None =>
        }
      }
      case None =>
    }
  }

  private[this] def publishState(username: String, key: String, value: Any): Unit = {
    this.presences.get(username) match {
      case Some(presence) =>
        this.presences += (username -> presence.copy(state = presence.state + (key -> value)))
        this.broadcastToSubscribed(username, UserPresencePublishState(username, key, value))
      case None =>
      // TODO Error
    }
  }

  private[this] def clearState(username: String, key: String): Unit = {
    this.presences.get(username) match {
      case Some(presence) =>
        this.presences += (username -> presence.copy(state = presence.state - key))
        this.broadcastToSubscribed(username, UserPresenceClearState(username, key))
      case None =>
      // TODO Error
    }
  }

  private[this] def subscribe(usernames: List[String], client: ActorRef): Unit = {
    val result = usernames.map { username =>
      subscribe(username, client)
    }
    lookupPresence(usernames)
  }

  private[this] def subscribe(username: String, client: ActorRef): Unit = {
    this.subscriptions.subscribe(client, username)
    sender ! lookupPresence(List(username)).head
  }

  private[this] def unsubscribe(username: String, client: ActorRef): Unit = {
    this.subscriptions.unsubscribe(client, username)
  }

  private[this] def lookupPresence(usernames: List[String]): List[UserPresence] = {
    usernames.map { username =>
      this.presences.get(username) match {
        case Some(presence) =>
          presence
        case None =>
          UserPresence(username, false, Map(), Set())
      }
    }
  }

  private[this] def broadcastToSubscribed(username: String, message: Any): Unit = {
    this.subscriptions.subscribers(username) foreach { client =>
      client ! message
    }
  }
  
  private[this] def handleDeathwatch(client: ActorRef): Unit = {
    this.subscriptions.unsubscribe(client)
    userDiconnected(client)
  }
}
