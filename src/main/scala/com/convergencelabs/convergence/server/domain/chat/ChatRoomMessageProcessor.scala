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

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.AddUserToChannelRequest
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.ExistingChatMessage
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.InvalidChatMessageExcpetion
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.JoinChannelRequest
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.LeaveChannelRequest

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import grizzled.slf4j.Logging

class ChatRoomMessageProcessor(
  domainFqn: DomainId,
  channelId: String,
  stateManager: ChatStateManager,
  private[this] val onEmpty: () => Unit,
  context: ActorContext)
    extends ChatMessageProcessor(stateManager)
    with Logging {

  private[this] val chatRoomSessionManager = new ChatRoomSessionManager()
  private[this] val watcher = context.system.actorOf(Props(new Watcher()))

  override def processChatMessage(message: ExistingChatMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case _: AddUserToChannelRequest =>
        Failure(new InvalidChatMessageExcpetion("Can not add user to a chat room"))
      case _: ExistingChatMessage =>
        super.processChatMessage(message)
    }
  }

  override def onJoinChannel(message: JoinChannelRequest): Try[ChatMessageProcessingResult] = {
    val JoinChannelRequest(domainFqn, channelId, requestor, client) = message
    logger.debug(s"Client(${requestor}) joined chat room: ${channelId}")

    (chatRoomSessionManager.join(requestor, client) match {
      case true =>
        // First session in, process the join request normally
        super.onJoinChannel(message)
      case false =>
        // user is already in, so short circuit
        Success(ChatMessageProcessingResult(Some(createJoinResponse()), List()))
    }).map { result => 
      watcher.tell(client, Actor.noSender)
      result
    }
  }

  override def onLeaveChannel(message: LeaveChannelRequest): Try[ChatMessageProcessingResult] = {
    val LeaveChannelRequest(domainFqn, channelId, userSession, client) = message
    logger.debug(s"Client(${userSession}) left chat room: ${channelId}")
    val result = chatRoomSessionManager.leave(userSession.sessionId) match {
      case true =>
        super.onLeaveChannel(message)
      case false =>
        // User has more sessions, so no need to broadcast anything, or change state
        Success(ChatMessageProcessingResult(Some(()), List()))
    }

    if (stateManager.state().members.isEmpty) {
      this.debug("Last session left chat room, requesting passivation")
      this.onEmpty()
    }

    result
  }

  def boradcast(message: Any): Unit = {
    chatRoomSessionManager.connectedClients().foreach(client => {
      client ! message
    })
  }

  /**
   * A helper actor that watches chat clients and helps notify us that a client
   * has left.
   */
  class Watcher() extends Actor with ActorLogging {
    def receive = {
      case client: ActorRef =>
        context.watch(client)
      case Terminated(client) =>
        context.unwatch(client)
        chatRoomSessionManager.getSession(client).foreach { sk =>
          // TODO This is a little sloppy since we will send a message to the client, which we already know is gone.
          val syntheticMessage = LeaveChannelRequest(domainFqn, channelId, sk, client)
          processChatMessage(syntheticMessage) recover {
            case cause: Throwable => 
              log.error(cause, "Error leaving channel after client actor terminated")
          }
        }
    }
  }
}