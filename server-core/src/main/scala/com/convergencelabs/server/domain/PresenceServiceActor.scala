package com.convergencelabs.server.domain

import com.convergencelabs.server.domain.model.SessionKey

import PresenceServiceActor._
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import com.convergencelabs.server.util.SubscriptionMap
import akka.actor.Terminated

// FIXME This entire actor needs to be re-designed. This does not scale at all
// we probably need to store presence state / data in the database so that
// this can become stateless. Then we can use distributed pub-sub as mechanism
// for persistence subscriptions.
object PresenceServiceActor {

  val RelativePath = "presenceService"

  def props(domainFqn: DomainFqn): Props = Props(
    new PresenceServiceActor(domainFqn))

  case class PresenceRequest(usernames: List[String])
  case class UserPresence(username: String, available: Boolean, state: Map[String, Any], clients: Set[ActorRef])

  case class UserConnected(username: String, client: ActorRef)
  
  case class UserPresenceSetState(username: String, state: Map[String, Any])
  case class UserPresenceRemoveState(username: String, keys: List[String])
  case class UserPresenceClearState(username: String)
  case class UserPresenceAvailability(username: String, available: Boolean)

  case class SubscribePresence(usernames: List[String], client: ActorRef)
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
    case UserPresenceSetState(username, state) =>
      setState(username, state)
    case UserPresenceRemoveState(username, key) =>
      removeState(username, key)
    case UserPresenceClearState(username) =>
      clearState(username)
    case SubscribePresence(usernames, client) =>
      subscribe(usernames, client)
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

  private[this] def setState(username: String, state: Map[String, Any]): Unit = {
    this.presences.get(username) match {
      case Some(presence) =>
        state foreach { case (k, v) =>
          this.presences += (username -> presence.copy(state = presence.state + (k -> v)))  
        }
        this.broadcastToSubscribed(username, UserPresenceSetState(username, state))
      case None =>
      // TODO Error
    }
  }
  
  private[this] def clearState(username: String): Unit = {
    this.presences.get(username) match {
      case Some(presence) =>
        this.presences += (username -> presence.copy(state = Map()))
        this.broadcastToSubscribed(username, UserPresenceClearState(username))
      case None =>
      // TODO Error
    }
  }

  private[this] def removeState(username: String, keys: List[String]): Unit = {
    this.presences.get(username) match {
      case Some(presence) =>
        keys foreach { key =>
          this.presences += (username -> presence.copy(state = presence.state - key))  
        }
        this.broadcastToSubscribed(username, UserPresenceRemoveState(username, keys))
      case None =>
      // TODO Error
    }
  }

  private[this] def subscribe(usernames: List[String], client: ActorRef): Unit = {
    val result = usernames.map { username =>
      this.subscriptions.subscribe(client, username)
    }
    sender ! lookupPresence(usernames)
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
