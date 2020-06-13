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
import com.convergencelabs.convergence.server.datastore.domain.{ChatMember, ChatStore, ChatUserAddedEvent, PermissionsStore}
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{AddUserToChatRequest, AddUserToChatResponse, CommonErrors}
import com.convergencelabs.convergence.server.domain.chat.ChatPermissionResolver.hasPermissions
import com.convergencelabs.convergence.server.domain.chat.processors.ReplyAndBroadcastTask
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatPermissions, ChatState}

import scala.util.Try

/**
 * The [[AddUserEventProcessor]] provides helper methods to process
 * the [[AddUserToChatRequest]].
 */
private[chat] object AddUserEventProcessor
  extends ChatEventMessageProcessor[AddUserToChatRequest, ChatUserAddedEvent, AddUserToChatResponse] {

  import ChatEventMessageProcessor._

  private val RequiredPermission = ChatPermissions.Permissions.AddUser

  def execute(message: ChatActor.AddUserToChatRequest,
              state: ChatState,
              chatStore: ChatStore,
              permissionsStore: PermissionsStore): ChatEventMessageProcessorResult[AddUserToChatResponse] =
    process(
      message = message,
      state = state,
      checkPermissions = hasPermissions(chatStore, permissionsStore, message.chatId, RequiredPermission),
      validateMessage = validateMessage,
      createEvent = createEvent,
      persistEvent = persistEvent(chatStore, permissionsStore),
      updateState = updateState,
      createSuccessReply = createSuccessReply,
      createErrorReply = value => ChatActor.AddUserToChatResponse(Left(value))
    )

  def validateMessage(message: AddUserToChatRequest, state: ChatState): Either[ChatActor.AddUserToChatResponse, Unit] = {
    if (!state.members.contains(message.requester)) {
      Left(ChatActor.AddUserToChatResponse(Left(ChatActor.ChatNotJoinedError())))
    } else if (state.members.contains(message.userToAdd)) {
      Left(ChatActor.AddUserToChatResponse(Left(ChatActor.AlreadyAMemberError())))
    } else {
      Right(())
    }
  }

  def createEvent(message: AddUserToChatRequest, state: ChatState): ChatUserAddedEvent =
    ChatUserAddedEvent(nextEvent(state), state.id, message.requester, timestamp(), message.userToAdd)

  def persistEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: ChatUserAddedEvent): Try[Unit] = {
    for {
      _ <- chatStore.addChatUserAddedEvent(event)
      chatRid <- chatStore.getChatRid(event.id)
      _ <- permissionsStore.addUserPermissions(ChatPermissions.DefaultChatPermissions, event.userAdded, Some(chatRid))
    } yield ()
  }

  def updateState(event: ChatUserAddedEvent, state: ChatState): ChatState = {
    val newMembers = state.members + (event.userAdded -> ChatMember(event.id, event.userAdded, 0))
    state.copy(lastEventNumber = event.eventNumber, lastEventTime = event.timestamp, members = newMembers)
  }

  def createSuccessReply(message: AddUserToChatRequest,
                         event: ChatUserAddedEvent,
                         state: ChatState): ReplyAndBroadcastTask[AddUserToChatResponse] = {
    replyAndBroadcastTask(
      message.replyTo,
      AddUserToChatResponse(Right(())),
      Some(ChatClientActor.UserAddedToChat(event.id, event.eventNumber, event.timestamp, event.user, event.userAdded))
    )
  }

  def createErrorReply(error: CommonErrors): AddUserToChatResponse = {
    ChatActor.AddUserToChatResponse(Left(error))
  }
}
