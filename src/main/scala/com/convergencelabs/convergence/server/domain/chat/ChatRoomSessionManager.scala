/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.chat

import akka.actor.ActorRef
import com.convergencelabs.convergence.server.domain.{DomainUserId, DomainUserSessionId}

private[chat] class ChatRoomSessionManager {

  private[this] var clients = Set[ActorRef]()
  private[this] var clientSessionMap = Map[ActorRef, DomainUserSessionId]()
  private[this] var sessionClientMap = Map[DomainUserSessionId, ActorRef]()
  private[this] var userSessionMap = Map[DomainUserId, Set[DomainUserSessionId]]()

  def join(domainSessionId: DomainUserSessionId, client: ActorRef): Boolean = {
    val userId = domainSessionId.userId
    val userSessions = this.userSessionMap.getOrElse(userId, Set())
    val newSessions = userSessions + domainSessionId
    this.userSessionMap += (userId -> newSessions)
    clientSessionMap += (client -> domainSessionId)
    sessionClientMap += (domainSessionId -> client)
    this.clients += client
    newSessions.size == 1
  }

  def remove(userId: DomainUserId): Unit = {
    this.userSessionMap.get(userId) foreach { sessions =>
      sessions.foreach(leave)
    }
  }

  def leave(domainSessionId: DomainUserSessionId): Boolean = {
    val client = this.sessionClientMap(domainSessionId)
    val userId = domainSessionId.userId
    val userSessions = this.userSessionMap.getOrElse(userId, Set())
    val newSessions = userSessions - domainSessionId
    if (newSessions.isEmpty) {
      this.userSessionMap -= userId
    } else {
      this.userSessionMap += (userId -> newSessions)
    }

    clientSessionMap -= client
    sessionClientMap -= domainSessionId
    clients -= client
    newSessions.isEmpty
  }

  def isConnected(session: DomainUserSessionId): Boolean = {
    this.sessionClientMap.contains(session)
  }

  def connectedClients(): Set[ActorRef] = {
    clients
  }

  def getSession(client: ActorRef): Option[DomainUserSessionId] = clientSessionMap.get(client)

  def getClient(domainSessionId: DomainUserSessionId): Option[ActorRef] = sessionClientMap.get(domainSessionId)
}