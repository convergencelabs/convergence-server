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

package com.convergencelabs.convergence.server.domain.chat.processors

import akka.actor.typed.ActorRef
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.domain.DomainUserId



private[chat] class ChatRoomClientManager {
  type Client = ActorRef[ChatClientActor.OutgoingMessage]

  private[this] var clients = Set[Client]()
  private[this] var clientUserMap = Map[Client, DomainUserId]()
  private[this] var clientsByUser = Map[DomainUserId, Set[Client]]()

  def join(userId: DomainUserId, client: ActorRef[ChatClientActor.OutgoingMessage]): Unit = {
    val clientForUser = this.clientsByUser.getOrElse(userId, Set())
    val updatedClients = clientForUser + client
    this.clientsByUser += (userId -> updatedClients)
    clientUserMap += (client -> userId)
    this.clients += client
  }

  def remove(userId: DomainUserId): Unit = {
    this.clientsByUser.get(userId) foreach { clients =>
      clients.foreach(leave)
    }
  }

  def leave(client: Client): Unit = {
    val userId = this.clientUserMap(client)
    val clientForUser = this.clientsByUser.getOrElse(userId, Set())
    val updatedClients = clientForUser - client
    if (updatedClients.isEmpty) {
      this.clientsByUser -= userId
    } else {
      this.clientsByUser += (userId -> updatedClients)
    }

    clientUserMap -= client
    clients -= client
  }

  def isJoined(client: Client): Boolean = {
    this.clients.contains(client)
  }

  def isJoined(user: DomainUserId): Boolean = {
    this.clientsByUser.contains(user)
  }

  def joinedClients(): Set[ActorRef[ChatClientActor.OutgoingMessage]] = {
    clients
  }

  def getUser(client: Client): Option[DomainUserId] = clientUserMap.get(client)
}