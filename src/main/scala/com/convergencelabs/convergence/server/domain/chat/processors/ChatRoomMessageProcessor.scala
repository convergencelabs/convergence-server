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
import com.convergencelabs.convergence.common.Ok
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
    if (state.members.isEmpty) {
      ChatMessageProcessor.Passivate
    } else {
      result
    }
  }

  override def onAddUserToChatRequest(msg: AddUserToChatRequest): ChatEventMessageProcessorResult[AddUserToChatResponse] =
    ChatEventMessageProcessorResult(None,
      ReplyAndBroadcastTask(MessageReplyTask(msg.replyTo, AddUserToChatResponse(Left(ChatOperationNotSupported("Can not add user to a chat room")))), None))

  override def onJoinChatRequest(message: JoinChatRequest): ChatEventMessageProcessorResult[JoinChatResponse] = {
    val JoinChatRequest(_, chatId, requester, client, _) = message
    logger.debug(s"Client($requester) requested to join chat room: $chatId")

    if (!clientManager.isJoined(client)) {
      // This client is not joined yet, so we can process the request.

      if (!clientManager.isJoined(requester)) {
        // The user has not joined yet. So we want to run the normal join
        // process.

        val result = super.onJoinChatRequest(message)
        // we peek into the result and see if the join was a success. If so, we
        // let the client manager know.
        if (result.task.reply.response.info.isRight) {
          clientManager.join(requester, client)
          clientWatcher ! ChatRoomClientWatcher.Watch(client)
        }

        result
      } else {
        // We know this user is allowed to join since they already
        // have a session.
        clientManager.join(requester, client)
        clientWatcher ! ChatRoomClientWatcher.Watch(client)

        val action = ReplyAndBroadcastTask(
          MessageReplyTask(message.replyTo, JoinChatResponse(Right(JoinEventProcessor.stateToInfo(chatState)))),
          None
        )

        ChatEventMessageProcessorResult(None, action)
      }
    } else {
      // This session / client is already in the room.
      val action = ReplyAndBroadcastTask(
        MessageReplyTask(message.replyTo, JoinChatResponse(Left(ChatAlreadyJoinedError()))),
        None
      )

      ChatEventMessageProcessorResult(None, action)
    }
  }

  override def onLeaveChatRequest(message: LeaveChatRequest): ChatEventMessageProcessorResult[LeaveChatResponse] = {
    val LeaveChatRequest(_, chatId, requester, client, _) = message

    if (clientManager.isJoined(client)) {
      // The client is joined, so we can process the leave.
      logger.debug(s"Client($client) left chat room: $chatId")

      this.clientWatcher ! ChatRoomClientWatcher.Unwatch(client)
      clientManager.leave(client)

      if (!clientManager.isJoined(requester)) {
        // Th user is not longer joined to the room. So process the leave.
        super.onLeaveChatRequest(message)
      } else {
        // The user still has more sessions so we just respond to this
        // request.
        val action = ReplyAndBroadcastTask(MessageReplyTask(message.replyTo, LeaveChatResponse(Right(Ok()))), None)
        ChatEventMessageProcessorResult(None, action)
      }
    } else {
      // The client is not joined to this room so we returned an error.
      val action = ReplyAndBroadcastTask(
        MessageReplyTask(message.replyTo, LeaveChatResponse(Left(ChatNotJoinedError()))), None)
      ChatEventMessageProcessorResult(None, action)
    }
  }

  def broadcast(message: ChatClientActor.OutgoingMessage): Unit = {
    clientManager.joinedClients().foreach(client => {
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