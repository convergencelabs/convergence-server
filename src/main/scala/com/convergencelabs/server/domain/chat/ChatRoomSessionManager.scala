/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.chat

import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.DomainUserSessionId

import akka.actor.ActorRef

class ChatRoomSessionManager {
  
  var clients = Set[ActorRef]()
  var clientSessionMap = Map[ActorRef, DomainUserSessionId]()
  var sessionClientMap = Map[String, ActorRef]()
  var userSessionMap = Map[DomainUserId, Set[String]]()

  def join(domainSessionId: DomainUserSessionId, client: ActorRef): Boolean = {
    val userId = domainSessionId.userId
    val userSessions = this.userSessionMap.getOrElse(userId, Set())
    val newSessions = userSessions + domainSessionId.sessionId
    this.userSessionMap += (userId -> newSessions)
    clientSessionMap += (client -> domainSessionId)
    sessionClientMap += (domainSessionId.sessionId -> client)
    this.clients += client
    newSessions.size == 1
  }
  
  def remove(userId: DomainUserId): Unit = {
    this.userSessionMap.get(userId) foreach { sessions => 
      sessions.foreach(leave(_))
    }
  }

  def leave(domainSessionId: String): Boolean = {
    val client = this.sessionClientMap.get(domainSessionId).getOrElse {
      throw new IllegalArgumentException("No such session")
    }
    leave(client)
  }

  def leave(client: ActorRef): Boolean = {
    val domainSessionId = this.clientSessionMap.get(client).getOrElse {
      throw new IllegalArgumentException("No such client")
    }
    val userId = domainSessionId.userId
    val userSessions = this.userSessionMap.getOrElse(userId, Set())
    val newSessions = userSessions - domainSessionId.sessionId
    if (newSessions.isEmpty) {
      this.userSessionMap -= userId
    } else {
      this.userSessionMap += (userId -> newSessions)
    }

    clientSessionMap -= client
    sessionClientMap -= domainSessionId.sessionId
    clients -= client
    newSessions.isEmpty
  }
  
  def connectedClients(): Set[ActorRef] = {
    clients
  }

  def getSession(client: ActorRef): Option[DomainUserSessionId] = clientSessionMap.get(client)
  def getClient(domainSessionId: DomainUserSessionId): Option[ActorRef] = sessionClientMap.get(domainSessionId.sessionId)
}