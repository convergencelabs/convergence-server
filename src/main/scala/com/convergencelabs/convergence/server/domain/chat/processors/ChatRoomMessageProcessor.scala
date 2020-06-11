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

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.datastore.domain.{ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.chat.ChatActor._
import com.convergencelabs.convergence.server.domain.chat._
import com.convergencelabs.convergence.server.domain.chat.processors.event.{ChatEventMessageProcessorResult, JoinEventProcessor}
import grizzled.slf4j.Logging

/**
 *
 * @param chatState        The current state of the chat.
 * @param chatStore        The chat persistence store
 * @param permissionsStore The permissions persistence store.
 * @param domainId         The domainId of the domain this chat belongs to.
 * @param context          The ActorContext used to create child actors.
 */
private[chat] class ChatRoomMessageProcessor(chatState: ChatState,
                                             chatStore: ChatStore,
                                             permissionsStore: PermissionsStore,
                                             domainId: DomainId,
                                             context: ActorContext[ChatActor.Message])
  extends ChatMessageProcessor(chatState, chatStore, permissionsStore) with Logging {

  private[this] val chatRoomSessionManager = new ChatRoomClientManager()
  private[this] val watcher = context.spawnAnonymous(Watcher())

  override def onChatEventRequest(message: ChatEventRequest[_]): ChatMessageProcessor.NextBehavior = {
    val result = super.onChatEventRequest(message)
    if (this.chatState.members.isEmpty) {
      ChatMessageProcessor.Passivate
    } else {
      result
    }
  }

  override def onAddUserToChatRequest(msg: AddUserToChatRequest): ChatEventMessageProcessorResult =
    ChatEventMessageProcessorResult(None,
      ReplyAndBroadcastTask(MessageReplyTask(msg.replyTo, AddUserToChatResponse(Left(ChatOperationNotSupported("Can not add user to a chat room")))), None))

  override def onJoinChatRequest(message: JoinChatRequest): ChatEventMessageProcessorResult = {
    val JoinChatRequest(_, channelId, requester, client, _) = message
    logger.debug(s"Client($requester) joined chat room: $channelId")

    val result = if (chatRoomSessionManager.join(requester, client)) {
      super.onJoinChatRequest(message)
    } else {
      val action = ReplyAndBroadcastTask(
        MessageReplyTask(message.replyTo,
          JoinChatResponse(Right(JoinEventProcessor.stateToInfo(chatState)))),
        None
      )

      watcher ! Watcher.Watch(client)

      ChatEventMessageProcessorResult(None, action)
    }
    result
  }


  override def onLeaveChatRequest(message: LeaveChatRequest): ChatEventMessageProcessorResult = {
    val LeaveChatRequest(_, channelId, _, client, _) = message

    this.watcher ! Watcher.Unwatch(client)

    if (chatRoomSessionManager.isConnected(client)) {
      logger.debug(s"Client($client) left chat room: $channelId")
      val result = if (chatRoomSessionManager.leave(client)) {
        super.onLeaveChatRequest(message)
      } else {
        val action = ReplyAndBroadcastTask(MessageReplyTask(message.replyTo, LeaveChatResponse(Right(()))), None)
        ChatEventMessageProcessorResult(None, action)
      }

      result
    } else {
      ChatEventMessageProcessorResult(None,
        ReplyAndBroadcastTask(
          MessageReplyTask(message.replyTo, ChatActor.LeaveChatResponse(Left(ChatNotJoinedError()))),
          None)
      )
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

    sealed trait Message

    final case class Watch(actor: ActorRef[_]) extends Message

    final case class Unwatch(actor: ActorRef[_]) extends Message

    def apply(): Behavior[Message] = Behaviors.setup { c =>
      debug(msg = "Private Chat Room Watcher initializing")
      Behaviors.receiveMessage[Message] {
        case Watch(client) =>
          c.watch(client)
          Behaviors.same
        case Unwatch(client) =>
          c.unwatch(client)
          Behaviors.same
      }.receiveSignal {
        case (_, Terminated(client)) =>
          debug("Client actor terminated, leaving the chat room")
          context.unwatch(client)
          chatRoomSessionManager.getUser(client.asInstanceOf[ActorRef[ChatClientActor.OutgoingMessage]]).foreach { user =>
            val syntheticMessage = ChatActor.LeaveChatRequest(domainId, chatState.id, user, client.unsafeUpcast[ChatClientActor.OutgoingMessage], context.system.ignoreRef)
            context.self ! syntheticMessage
          }
          Behaviors.same
      }
    }
  }
}