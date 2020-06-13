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

import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.datastore.domain.{ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.chat.ChatActor._
import com.convergencelabs.convergence.server.domain.chat.processors.ChatMessageProcessor.NextBehavior
import com.convergencelabs.convergence.server.domain.chat.processors.event._
import com.convergencelabs.convergence.server.domain.chat.processors.general.{GetHistoryMessageProcessor, RemoveChatMessageProcessor}
import com.convergencelabs.convergence.server.domain.chat.processors.permissions._
import com.convergencelabs.convergence.server.domain.chat.{ChatPermissionResolver, ChatState}
import grizzled.slf4j.Logging

abstract class ChatMessageProcessor(protected var state: ChatState,
                                    chatStore: ChatStore,
                                    permissionsStore: PermissionsStore) extends Logging {

  final def processChatRequestMessage(message: ChatRequestMessage): NextBehavior = {
    message match {
      case message: ChatEventRequest[_] =>
        onChatEventRequest(message)
      case message: ChatPermissionsRequest[_] =>
        onChatPermissionsRequest(message)
      case message: RemoveChatRequest =>
        onRemoveChatRequest(message)
      case message: GetChatHistoryRequest =>
        onGetChatHistoryRequest(message)
    }
  }

  protected def onChatEventRequest(msg: ChatEventRequest[_]): NextBehavior = {
    val result: ChatEventMessageProcessorResult[_] = msg match {
      case message: JoinChatRequest =>
        onJoinChatRequest(message)
      case message: LeaveChatRequest =>
        onLeaveChatRequest(message)
      case message: AddUserToChatRequest =>
        onAddUserToChatRequest(message)
      case message: RemoveUserFromChatRequest =>
        onRemovedUserFromChatRequest(message)
      case message: SetChatNameRequest =>
        onSetChatNameRequest(message)
      case message: SetChatTopicRequest =>
        onSetChatTopicRequest(message)
      case message: MarkChatsEventsSeenRequest =>
        onMarkChatsEventsSeenRequest(message)
      case message: PublishChatMessageRequest =>
        onPublishChatMessageRequest(message)
    }

    replyAndBroadcast(result.task)

    result.newState.foreach { s =>
      this.state = s
    }

    ChatMessageProcessor.Same
  }

  protected def onJoinChatRequest(msg: JoinChatRequest): ChatEventMessageProcessorResult[JoinChatResponse] =
    JoinEventProcessor.execute(msg, state, chatStore, permissionsStore)

  protected def onLeaveChatRequest(msg: LeaveChatRequest): ChatEventMessageProcessorResult[LeaveChatResponse] =
    LeaveEventProcessor.execute(msg, state, chatStore, permissionsStore)

  protected def onAddUserToChatRequest(msg: AddUserToChatRequest): ChatEventMessageProcessorResult[AddUserToChatResponse] =
    AddUserEventProcessor.execute(msg, state, chatStore, permissionsStore)

  protected def onRemovedUserFromChatRequest(msg: RemoveUserFromChatRequest): ChatEventMessageProcessorResult[RemoveUserFromChatResponse] =
    RemoveUserEventProcessor.execute(msg, state, chatStore, permissionsStore)

  protected def onSetChatNameRequest(msg: SetChatNameRequest): ChatEventMessageProcessorResult[SetChatNameResponse] =
    SetNameEventProcessor.execute(msg, state, chatStore, permissionsStore)

  protected def onSetChatTopicRequest(msg: SetChatTopicRequest): ChatEventMessageProcessorResult[SetChatTopicResponse] =
    SetTopicEventProcessor.execute(msg, state, chatStore, permissionsStore)

  protected def onMarkChatsEventsSeenRequest(msg: MarkChatsEventsSeenRequest): ChatEventMessageProcessorResult[MarkChatsEventsSeenResponse] =
    MarkSeenEventProcessor.execute(msg, state, chatStore, permissionsStore)

  protected def onPublishChatMessageRequest(msg: PublishChatMessageRequest): ChatEventMessageProcessorResult[PublishChatMessageResponse] =
    PublishMessageEventProcessor.execute(msg, state, chatStore, permissionsStore)


  private[this] def onChatPermissionsRequest(message: ChatPermissionsRequest[_]): NextBehavior = {
    val result = message match {
      case msg: AddChatPermissionsRequest =>
        MessageReplyTask(msg.replyTo, AddChatPermissionsProcessor.execute(msg, chatStore.getChatRid, permissionsStore))
      case msg: RemoveChatPermissionsRequest =>
        MessageReplyTask(msg.replyTo, RemoveChatPermissionsProcessor.execute(msg, chatStore.getChatRid, permissionsStore))
      case msg: SetChatPermissionsRequest =>
        MessageReplyTask(msg.replyTo, SetChatPermissionsProcessor.execute(msg, chatStore.getChatRid, permissionsStore))
      case msg: GetClientChatPermissionsRequest =>
        MessageReplyTask(msg.replyTo, GetClientChatPermissionsProcessor.execute(msg, chatStore.getChatRid, permissionsStore))
      case msg: GetWorldChatPermissionsRequest =>
        MessageReplyTask(msg.replyTo, GetWorldChatPermissionsProcessor.execute(msg, chatStore.getChatRid, permissionsStore))
      case msg: GetAllUserChatPermissionsRequest =>
        MessageReplyTask(msg.replyTo, onGetAllUserChatPermissionsRequest(msg))
      case msg: GetAllGroupChatPermissionsRequest =>
        MessageReplyTask(msg.replyTo, onGetAllGroupChatPermissionsRequest(msg))
      case msg: GetUserChatPermissionsRequest =>
        MessageReplyTask(msg.replyTo, onGetUserChatPermissionsRequest(msg))
      case msg: GetGroupChatPermissionsRequest =>
        MessageReplyTask(msg.replyTo, onGetGroupChatPermissionsRequest(msg))
    }

    result.execute()

    ChatMessageProcessor.Same
  }

  protected def onGetWorldChatPermissionsRequest(msg: GetWorldChatPermissionsRequest): GetWorldChatPermissionsResponse =
    GetWorldChatPermissionsProcessor.execute(msg, chatStore.getChatRid, permissionsStore)

  protected def onGetAllUserChatPermissionsRequest(msg: GetAllUserChatPermissionsRequest): GetAllUserChatPermissionsResponse =
    GetAllUserChatPermissionsProcessor.execute(msg, chatStore.getChatRid, permissionsStore)

  protected def onGetAllGroupChatPermissionsRequest(msg: GetAllGroupChatPermissionsRequest): GetAllGroupChatPermissionsResponse =
    GetAllGroupChatPermissionsProcessor.execute(msg, chatStore.getChatRid, permissionsStore)

  protected def onGetUserChatPermissionsRequest(msg: GetUserChatPermissionsRequest): GetUserChatPermissionsResponse =
    GetUserChatPermissionsProcessor.execute(msg, chatStore.getChatRid, permissionsStore)

  protected def onGetGroupChatPermissionsRequest(msg: GetGroupChatPermissionsRequest): GetGroupChatPermissionsResponse =
    GetGroupChatPermissionsProcessor.execute(msg, chatStore.getChatRid, permissionsStore)

  protected def onRemoveChatRequest(message: RemoveChatRequest): NextBehavior = {
    val response = RemoveChatMessageProcessor.execute(
      message = message,
      removeChat = chatStore.removeChat,
      checkPermissions = ChatPermissionResolver.hasPermissions(chatStore, permissionsStore, message.chatId)
    )

    replyAndBroadcast(response)

    ChatMessageProcessor.Stop
  }

  protected def onGetChatHistoryRequest(message: GetChatHistoryRequest): NextBehavior = {
    val response = GetHistoryMessageProcessor.execute(
      message = message,
      getHistory = chatStore.getChatEvents,
      checkPermissions = ChatPermissionResolver.hasPermissions(chatStore, permissionsStore, message.chatId)
    )

    message.replyTo ! response

    ChatMessageProcessor.Same
  }

  private[this] def replyAndBroadcast[T](task: ReplyAndBroadcastTask[_]): Unit = {
    task.reply.execute()
    task.broadcast.foreach(broadcast)
  }

  def removeAllMembers(): Unit = {
    state.members.values.foreach(member => {
      chatStore.removeChatMember(state.id, member.userId) recover {
        case cause: Throwable =>
          error("Error removing chat channel member", cause)
      }
    })

    this.state = this.state.copy(members = Map())
  }

  protected def broadcast(message: ChatClientActor.OutgoingMessage): Unit
}

object ChatMessageProcessor {
  sealed trait NextBehavior
  case object Same extends NextBehavior
  case object Passivate extends NextBehavior
  case object Stop extends NextBehavior
}
