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

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.chat.ChatActor._
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

private[chat] class ChatRoomMessageProcessor(domainFqn: DomainId,
                                             channelId: String,
                                             stateManager: ChatStateManager,
                                             private[this] val onEmpty: () => Unit,
                                             private[this] val onMessage: Message => Unit,
                                             context: ActorContext[_])
  extends ChatMessageProcessor(stateManager)
    with Logging {

  private[this] val chatRoomSessionManager = new ChatRoomSessionManager()
  private[this] val watcher = context.spawnAnonymous(Watcher())

  override def processChatMessage(message: RequestMessage): Try[ChatMessageProcessingResult[_]] = {
    message match {
      case _: AddUserToChatRequest =>
        Failure(InvalidChatMessageException("Can not add user to a chat room"))
      case _: Message =>
        super.processChatMessage(message)
    }
  }

  override def onJoinChannel(message: JoinChatRequest): Try[ChatMessageProcessingResult[_]] = {
    val JoinChatRequest(_, channelId, requester, client, replyTo) = message
    logger.debug(s"Client($requester) joined chat room: $channelId")

    (if (chatRoomSessionManager.join(requester, client)) {
      super.onJoinChannel(message)
    } else {
      val response = Reply(createJoinResponse(), replyTo)
      Success(ChatMessageProcessingResult(Some(response), List()))
    }).map { result =>
      watcher ! Watcher.Watch(client)
      result
    }
  }

  override def onLeaveChannel(message: LeaveChatRequest): Try[ChatMessageProcessingResult[_]] = {
    val LeaveChatRequest(_, channelId, userSession, client, replyTo) = message

    this.watcher ! Watcher.Unwatch(client)

    if (chatRoomSessionManager.isConnected(userSession)) {
      logger.debug(s"Client($userSession) left chat room: $channelId")
      val result = if (chatRoomSessionManager.leave(userSession)) {
        super.onLeaveChannel(message)
      } else {
        val response = Reply(ChatActor.RequestSuccess(), replyTo)
        Success(ChatMessageProcessingResult(Some(response), List()))
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

  def broadcast(message: ChatClientActor.OutgoingMessage): Unit = {
    chatRoomSessionManager.connectedClients().foreach(client => {
      client ! message
    })
  }


  /**
   * A helper actor that watches chat clients and helps notify us that a client
   * has left.
   */
  private object Watcher extends Logging {

    trait Message

    case class Watch(actor: ActorRef[_]) extends Message

    case class Unwatch(actor: ActorRef[_]) extends Message

    def apply(): Behavior[Message] = Behaviors.setup { context =>
      debug(msg = "UserSessionTokenReaperActor initializing")
      Behaviors.receiveMessage[Message] {
        case Watch(client) =>
          context.watch(client)
          Behaviors.same
        case Unwatch(client) =>
          context.unwatch(client)
          Behaviors.same
      }.receiveSignal {
        case (_, Terminated(client)) =>
          debug("Client actor terminated, leaving the chat room")
          context.unwatch(client)
          chatRoomSessionManager.getSession(client.asInstanceOf[ActorRef[ChatClientActor.OutgoingMessage]]).foreach { sk =>
            // TODO This is a little sloppy since we will send a message to the client, which we already know is gone.
            val syntheticMessage = ChatActor.LeaveChatRequest(domainFqn, channelId, sk, client.unsafeUpcast[ChatClientActor.OutgoingMessage], context.system.ignoreRef)
            onMessage(syntheticMessage)
          }
          Behaviors.same
      }
    }
  }
}