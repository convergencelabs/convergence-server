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

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, Props, Terminated}
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.chat.ChatMessages._
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

private[chat] class ChatRoomMessageProcessor(domainFqn: DomainId,
                                             channelId: String,
                                             stateManager: ChatStateManager,
                                             private[this] val onEmpty: () => Unit,
                                             private[this] val onMessage: ExistingChatMessage => Unit,
                                             context: ActorContext)
  extends ChatMessageProcessor(stateManager)
    with Logging {

  private[this] val chatRoomSessionManager = new ChatRoomSessionManager()
  private[this] val watcher = context.system.actorOf(Props(new Watcher()))

  override def processChatMessage(message: ExistingChatMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case _: AddUserToChannelRequest =>
        Failure(InvalidChatMessageException("Can not add user to a chat room"))
      case _: ExistingChatMessage =>
        super.processChatMessage(message)
    }
  }

  override def onJoinChannel(message: JoinChannelRequest): Try[ChatMessageProcessingResult] = {
    val JoinChannelRequest(_, channelId, requester, client) = message
    logger.debug(s"Client($requester) joined chat room: $channelId")

    (if (chatRoomSessionManager.join(requester, client)) {
      super.onJoinChannel(message)
    } else {
      Success(ChatMessageProcessingResult(Some(createJoinResponse()), List()))
    }).map { result =>
      watcher.tell(Watch(client), Actor.noSender)
      result
    }
  }

  override def onLeaveChannel(message: LeaveChannelRequest): Try[ChatMessageProcessingResult] = {
    val LeaveChannelRequest(_, channelId, userSession, client) = message

    this.watcher.tell(Unwatch(client), Actor.noSender)

    if (chatRoomSessionManager.isConnected(userSession)) {
      logger.debug(s"Client($userSession) left chat room: $channelId")
      val result = if (chatRoomSessionManager.leave(userSession)) {
        super.onLeaveChannel(message)
      } else {
        Success(ChatMessageProcessingResult(Some(()), List()))
      }

      if (stateManager.state().members.isEmpty) {
        this.debug("Last session left chat room, requesting passivation")
        this.onEmpty()
      }

      result
    } else {
      Failure(ChatNotJoinedException(channelId))
    }
  }

  def broadcast(message: Any): Unit = {
    chatRoomSessionManager.connectedClients().foreach(client => {
      client ! message
    })
  }

  /**
   * A helper actor that watches chat clients and helps notify us that a client
   * has left.
   */
  private[this] class Watcher() extends Actor with ActorLogging {
    def receive: Receive = {
      case Watch(client) =>
        context.watch(client)
      case Unwatch(client) =>
        context.unwatch(client)
      case Terminated(client) =>
        log.debug("Client actor terminated, leaving the chat room")
        context.unwatch(client)
        chatRoomSessionManager.getSession(client).foreach { sk =>
          // TODO This is a little sloppy since we will send a message to the client, which we already know is gone.
          val syntheticMessage = LeaveChannelRequest(domainFqn, channelId, sk, client)
          onMessage(syntheticMessage)
        }
    }
  }

  private[this] case class Watch(actor: ActorRef)

  private[this] case class Unwatch(actor: ActorRef)

}