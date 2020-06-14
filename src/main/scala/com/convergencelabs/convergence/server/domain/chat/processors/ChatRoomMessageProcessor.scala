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

import akka.actor.typed.scaladsl.Behaviors
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
 * @param clientManager    The ActorContext used to create child actors.
 * @param clientWatcher    A helper actor that will watch joined clients.
 */
private[chat] class ChatRoomMessageProcessor(chatState: ChatState,
                                             chatStore: ChatStore,
                                             permissionsStore: PermissionsStore,
                                             domainId: DomainId,
                                             clientManager: ChatRoomClientManager,
                                             clientWatcher: ActorRef[ChatRoomClientWatcher.Message]
                                            )
  extends ChatMessageProcessor(chatState, chatStore, permissionsStore) with Logging {

  override def onChatEventRequest(message: ChatEventRequest[_]): ChatMessageProcessor.NextBehavior = {
    val result = super.onChatEventRequest(message)
    if (this.chatState.members.isEmpty) {
      ChatMessageProcessor.Passivate
    } else {
      result
    }
  }

  override def onAddUserToChatRequest(msg: AddUserToChatRequest): ChatEventMessageProcessorResult[AddUserToChatResponse] =
    ChatEventMessageProcessorResult(None,
      ReplyAndBroadcastTask(MessageReplyTask(msg.replyTo, AddUserToChatResponse(Left(ChatOperationNotSupported("Can not add user to a chat room")))), None))

  override def onJoinChatRequest(message: JoinChatRequest): ChatEventMessageProcessorResult[JoinChatResponse] = {
    val JoinChatRequest(_, channelId, requester, client, _) = message
    logger.debug(s"Client($requester) requested to join chat room: $channelId")

    // FIXME this is not right.  We need to just check if they are joined.  They might
    //  not have the permissions to join.
    val result = if (clientManager.join(requester, client)) {
      super.onJoinChatRequest(message)
    } else {
      val action = ReplyAndBroadcastTask(
        MessageReplyTask(message.replyTo,
          JoinChatResponse(Right(JoinEventProcessor.stateToInfo(chatState)))),
        None
      )

      clientWatcher ! ChatRoomClientWatcher.Watch(client)

      ChatEventMessageProcessorResult(None, action)
    }
    result
  }


  override def onLeaveChatRequest(message: LeaveChatRequest): ChatEventMessageProcessorResult[LeaveChatResponse] = {
    val LeaveChatRequest(_, channelId, _, client, _) = message

    this.clientWatcher ! ChatRoomClientWatcher.Unwatch(client)

    if (clientManager.isConnected(client)) {
      logger.debug(s"Client($client) left chat room: $channelId")
      val result = if (clientManager.leave(client)) {
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
    clientManager.connectedClients().foreach(client => {
      client ! message
    })
  }
}

/**
 * A helper actor that watches chat clients and helps notify us that a client
 * has left.
 */
object ChatRoomClientWatcher extends Logging {

  sealed trait Message

  final case class Watch(actor: ActorRef[ChatClientActor.OutgoingMessage]) extends Message

  final case class Unwatch(actor: ActorRef[ChatClientActor.OutgoingMessage]) extends Message

  def apply(chat: ActorRef[ChatActor.Message],
            domainId: DomainId,
            id: String,
            chatRoomClientManager: ChatRoomClientManager): Behavior[Message] =
    Behaviors.setup { context =>
      debug(msg = "Private Chat Room Watcher initializing")
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
          chatRoomClientManager.getUser(client.asInstanceOf[ActorRef[ChatClientActor.OutgoingMessage]]).foreach { user =>
            val syntheticMessage = ChatActor.LeaveChatRequest(domainId, id, user, client.unsafeUpcast[ChatClientActor.OutgoingMessage], context.system.ignoreRef)
            chat ! syntheticMessage
          }
          Behaviors.same
      }
    }
}