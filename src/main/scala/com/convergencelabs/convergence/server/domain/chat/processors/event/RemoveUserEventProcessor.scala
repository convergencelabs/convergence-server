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
import com.convergencelabs.convergence.server.datastore.domain.{ChatStore, ChatUserRemovedEvent, PermissionsStore}
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{CommonErrors, RemoveUserFromChatRequest, RemoveUserFromChatResponse}
import com.convergencelabs.convergence.server.domain.chat.ChatPermissionResolver.hasPermissions
import com.convergencelabs.convergence.server.domain.chat.processors.ReplyAndBroadcastTask
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatPermissions, ChatState}

import scala.util.Try

object RemoveUserEventProcessor extends ChatEventMessageProcessor[RemoveUserFromChatRequest, ChatUserRemovedEvent, RemoveUserFromChatResponse] {
  import ChatEventMessageProcessor._

  private val RequiredPermission = ChatPermissions.Permissions.RemoveUser

  def execute(message: ChatActor.RemoveUserFromChatRequest,
              state: ChatState,
              chatStore: ChatStore,
              permissionsStore: PermissionsStore): ChatEventMessageProcessorResult =
    process(
      message = message,
      state = state,
      checkPermissions = hasPermissions(chatStore, permissionsStore, message.chatId, RequiredPermission),
      validateMessage = validateMessage,
      createEvent = createEvent,
      persistEvent = processEvent(chatStore, permissionsStore),
      updateState = updateState,
      createSuccessReply = createSuccessReply,
      createErrorReply = value => ChatActor.RemoveUserFromChatResponse(Left(value))
    )

  def validateMessage(message: RemoveUserFromChatRequest, state: ChatState): Either[ChatActor.RemoveUserFromChatResponse, Unit] = {
    if (!state.members.contains(message.requester)) {
      Left(ChatActor.RemoveUserFromChatResponse(Left(ChatActor.ChatNotJoinedError())))
    } else {
      Right(())
    }
  }

  def createEvent(message: RemoveUserFromChatRequest, state: ChatState): ChatUserRemovedEvent =
    ChatUserRemovedEvent(nextEvent(state), state.id, message.requester, timestamp(), message.userToRemove)

  def processEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: ChatUserRemovedEvent): Try[Unit] = {
    for {
      _ <- chatStore.addChatUserRemovedEvent(event)
      chatRid <- chatStore.getChatRid(event.id)
      _ <- permissionsStore.removeUserPermissions(ChatPermissions.AllExistingChatPermissions, event.userRemoved, Some(chatRid))
    } yield {

    }
  }

  def updateState(event: ChatUserRemovedEvent, state: ChatState): ChatState = {
    val newMembers = state.members - event.userRemoved
    state.copy(lastEventNumber = event.eventNumber, lastEventTime = event.timestamp, members = newMembers)
  }

  def createSuccessReply(message: RemoveUserFromChatRequest, event: ChatUserRemovedEvent, state: ChatState): ReplyAndBroadcastTask = {
    replyAndBroadcastTask(
      message.replyTo,
      RemoveUserFromChatResponse(Right(())),
      Some(ChatClientActor.UserAddedToChat(event.id, event.eventNumber, event.timestamp, event.user, event.userRemoved))
    )
  }

  def createErrorReply(error: CommonErrors): RemoveUserFromChatResponse = {
    ChatActor.RemoveUserFromChatResponse(Left(error))
  }
}
