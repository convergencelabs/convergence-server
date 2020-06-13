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

package com.convergencelabs.convergence.server.domain.chat.processors.event

import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.datastore.domain.{ChatStore, ChatUserLeftEvent, PermissionsStore}
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{CommonErrors, JoinChatRequest, LeaveChatRequest, LeaveChatResponse}
import com.convergencelabs.convergence.server.domain.chat.ChatPermissionResolver.hasPermissions
import com.convergencelabs.convergence.server.domain.chat.processors.ReplyAndBroadcastTask
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatPermissions, ChatState}

import scala.util.Try


/**
 * The [[LeaveEventProcessor]] provides helper methods to process
 * the [[LeaveChatRequest]].
 */
private[chat] object LeaveEventProcessor extends ChatEventMessageProcessor[LeaveChatRequest, ChatUserLeftEvent, LeaveChatResponse] {

  import ChatEventMessageProcessor._

  private  val RequiredPermission = ChatPermissions.Permissions.LeaveChat

  def execute(message: ChatActor.LeaveChatRequest,
              state: ChatState,
              chatStore: ChatStore,
              permissionsStore: PermissionsStore): ChatEventMessageProcessorResult =
    process(
      message = message,
      state = state,
      checkPermissions = hasPermissions(chatStore, permissionsStore, message.chatId, RequiredPermission),
      validateMessage = validateMessage,
      createEvent = createEvent,
      persistEvent = persistEvent(chatStore, permissionsStore),
      updateState = updateState,
      createSuccessReply = createSuccessReply,
      createErrorReply = value => ChatActor.LeaveChatResponse(Left(value))
    )

  def validateMessage(message: LeaveChatRequest, state: ChatState): Either[ChatActor.LeaveChatResponse, Unit] = {
    if (!state.members.contains(message.requester)) {
      Left(ChatActor.LeaveChatResponse(Left(ChatActor.ChatNotJoinedError())))
    } else {
      Right(())
    }
  }

  def createEvent(message: LeaveChatRequest, state: ChatState): ChatUserLeftEvent =
    ChatUserLeftEvent(nextEvent(state), state.id, message.requester, timestamp())

  def persistEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: ChatUserLeftEvent): Try[Unit] = {
    for {
      _ <- chatStore.addChatUserLeftEvent(event)
      _ <- chatStore.removeChatMember(event.id, event.user)
    } yield ()
  }

  def updateState(event: ChatUserLeftEvent, state: ChatState): ChatState = {
    val newMembers = state.members - event.user
    state.copy(lastEventNumber = event.eventNumber, lastEventTime = event.timestamp, members = newMembers)
  }

  def createSuccessReply(message: LeaveChatRequest, event: ChatUserLeftEvent, state: ChatState): ReplyAndBroadcastTask = {
    replyAndBroadcastTask(
      message.replyTo,
      LeaveChatResponse(Right(())),
      Some(ChatClientActor.UserLeftChat(event.id, event.eventNumber, event.timestamp, event.user))
    )
  }

  def createErrorReply(error: CommonErrors): LeaveChatResponse = {
    ChatActor.LeaveChatResponse(Left(error))
  }
}
