/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.presence

import org.json4s.JsonAST.JValue

import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.util.SubscriptionMap

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.cluster.pubsub.DistributedPubSub

// FIXME This entire actor needs to be re-designed. This does not scale at all
// we probably need to store presence state / data in the database so that
// this can become stateless. Then we can use distributed pub-sub as mechanism
// for persistence subscriptions.
object PresenceServiceActor {

  val RelativePath = "presenceService"

  def props(domainFqn: DomainId): Props = Props(
    new PresenceServiceActor(domainFqn))
  
}

class PresenceServiceActor private[domain] (domainFqn: DomainId) extends Actor with ActorLogging {

  private var presences = Map[DomainUserId, UserPresence]()
  
  // FIXME These are particularly problematic.  We are storing actor refs and userIds
  // in memory.  there is not a good way to persist this.
  private var subscriptions = SubscriptionMap[ActorRef, DomainUserId]()
  private var clients = Map[ActorRef, DomainUserId]()
  
  val pubSubMediator = DistributedPubSub(context.system).mediator

  
  def receive: Receive = {
    case PresenceRequest(userIds) =>
      getPresence(userIds)
    case UserConnected(userId, client) =>
      userConnected(userId, client)
    case UserPresenceSetState(userId, state) =>
      setState(userId, state)
    case UserPresenceRemoveState(userId, key) =>
      removeState(userId, key)
    case UserPresenceClearState(userId) =>
      clearState(userId)
    case SubscribePresence(userIds, client) =>
      subscribe(userIds, client)
    case UnsubscribePresence(userIds, client) =>
      unsubscribe(userIds, client)
    case Terminated(client) =>
      handleDeathwatch(client)
  }

  private[this] def getPresence(userIds: List[DomainUserId]): Unit = {
    sender ! lookupPresence(userIds)
  }

  private[this] def userConnected(userId: DomainUserId, client: ActorRef): Unit = {
    this.subscriptions.subscribe(client, userId)
    
    clients += (client -> userId)
    val result = this.presences.get(userId) match {
      case Some(presence) =>
        presence.copy(clients = presence.clients + client)
      case None => {
        this.broadcastToSubscribed(userId, UserPresenceAvailability(userId, true))
        UserPresence(userId, true, Map(), Set(client))
      }
    }
    
    context.watch(client)
    this.presences += (userId -> result)
  }

  private[this] def userDisconnected(client: ActorRef): Unit = {
    clients.get(client) match {
      case Some(userId) => {
        clients -= client
        this.presences.get(userId) match {
          case Some(presence) =>
            val updated = presence.copy(clients = presence.clients - client)
            if (updated.clients.isEmpty) {
              this.presences -= userId
              this.broadcastToSubscribed(userId, UserPresenceAvailability(userId, false))
            } else {
              this.presences += (userId -> updated)
            }
          case None =>
        }
      }
      case None =>
    }
  }

  private[this] def setState(userId: DomainUserId, state: Map[String, JValue]): Unit = {
    this.presences.get(userId) match {
      case Some(presence) =>
        state foreach { case (k, v) =>
          this.presences += (userId -> presence.copy(state = presence.state + (k -> v)))  
        }
        this.broadcastToSubscribed(userId, UserPresenceSetState(userId, state))
      case None =>
      // TODO Error
    }
  }
  
  private[this] def clearState(userId: DomainUserId): Unit = {
    this.presences.get(userId) match {
      case Some(presence) =>
        this.presences += (userId -> presence.copy(state = Map()))
        this.broadcastToSubscribed(userId, UserPresenceClearState(userId))
      case None =>
      // TODO Error
    }
  }

  private[this] def removeState(userId: DomainUserId, keys: List[String]): Unit = {
    this.presences.get(userId) match {
      case Some(presence) =>
        keys foreach { key =>
          this.presences += (userId -> presence.copy(state = presence.state - key))  
        }
        this.broadcastToSubscribed(userId, UserPresenceRemoveState(userId, keys))
      case None =>
      // TODO Error
    }
  }

  private[this] def subscribe(userIds: List[DomainUserId], client: ActorRef): Unit = {
    userIds.foreach { userId =>
      this.subscriptions.subscribe(client, userId)
    }
    sender ! lookupPresence(userIds)
  }

  private[this] def unsubscribe(userIds: List[DomainUserId], client: ActorRef): Unit = {
    userIds.foreach(userId => this.subscriptions.unsubscribe(client, userId))
  }

  private[this] def lookupPresence(userIds: List[DomainUserId]): List[UserPresence] = {
    userIds.map { userId =>
      this.presences.get(userId) match {
        case Some(presence) =>
          presence
        case None =>
          UserPresence(userId, false, Map(), Set())
      }
    }
  }

  private[this] def broadcastToSubscribed(userId: DomainUserId, message: Any): Unit = {
    val subscribers = this.subscriptions.subscribers(userId)
    subscribers foreach { client =>
      client ! message
    }
  }
  
  private[this] def handleDeathwatch(client: ActorRef): Unit = {
    this.subscriptions.unsubscribe(client)
    userDisconnected(client)
  }
}
