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
import com.convergencelabs.convergence.server.datastore.domain.{ChatNameChangedEvent, ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{CommonErrors, SetChatNameRequest, SetChatNameResponse}
import com.convergencelabs.convergence.server.domain.chat.ChatPermissionResolver.hasPermissions
import com.convergencelabs.convergence.server.domain.chat.processors.ReplyAndBroadcastTask
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatPermissions, ChatState}

import scala.util.Try

object SetNameEventProcessor extends ChatEventMessageProcessor[SetChatNameRequest, ChatNameChangedEvent, SetChatNameResponse] {

  private val RequiredPermission = ChatPermissions.Permissions.SetName

  def execute(message: ChatActor.SetChatNameRequest,
              state: ChatState,
              chatStore: ChatStore,
              permissionsStore: PermissionsStore): ChatEventMessageProcessorResult =
    process(
      state = state,
      message = message,
      checkPermissions = hasPermissions(chatStore, permissionsStore, message.chatId, RequiredPermission),
      validateMessage = validateMessage,
      createEvent = createEvent,
      processEvent = processEvent(chatStore, permissionsStore),
      updateState = updateState,
      createSuccessReply = createSuccessReply(state),
      createErrorReply = value => ChatActor.SetChatNameResponse(Left(value))
    )

  def validateMessage(message: SetChatNameRequest, state: ChatState): Either[ChatActor.SetChatNameResponse, Unit] = {
    if (!state.members.contains(message.requester)) {
      Left(ChatActor.SetChatNameResponse(Left(ChatActor.ChatNotJoinedError())))
    } else {
      Right(())
    }
  }

  def createEvent(message: SetChatNameRequest, state: ChatState): ChatNameChangedEvent =
    ChatNameChangedEvent(nextEvent(state), state.id, message.requester, timestamp(), message.name)

  def processEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: ChatNameChangedEvent): Try[Unit] = {
    for {
      _ <- chatStore.addChatNameChangedEvent(event)
      _ <- chatStore.updateChat(event.id, Some(event.name), None)
    } yield ()
  }

  def updateState(event: ChatNameChangedEvent, state: ChatState): ChatState = {
    state.copy(lastEventNumber = event.eventNumber, lastEventTime = event.timestamp, name = event.name)
  }

  def createSuccessReply(state: ChatState)(message: SetChatNameRequest, event: ChatNameChangedEvent): ReplyAndBroadcastTask = {
    replyAndBroadcastTask(
      message.replyTo,
      SetChatNameResponse(Right(())),
      Some(ChatClientActor.ChatNameChanged(event.id, event.eventNumber, event.timestamp, event.user, event.name))
    )
  }

  def createErrorReply(error: CommonErrors): SetChatNameResponse = {
    ChatActor.SetChatNameResponse(Left(error))
  }
}
