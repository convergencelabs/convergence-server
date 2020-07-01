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

package com.convergencelabs.convergence.server.backend.services.domain.chat.processors.event

import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.backend.datastore.domain.chat.ChatStore
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.PermissionsStore
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor.{CommonErrors, SetChatTopicRequest, SetChatTopicResponse}
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatPermissionResolver.hasPermissions
import com.convergencelabs.convergence.server.backend.services.domain.chat.processors.ReplyAndBroadcastTask
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatActor, ChatPermissions, ChatState}
import com.convergencelabs.convergence.server.model.domain.chat.ChatTopicChangedEvent

import scala.util.Try

/**
 * The [[SetTopicEventProcessor]] provides helper methods to process
 * the [[SetChatTopicRequest]].
 */
private[chat] object SetTopicEventProcessor
  extends ChatEventMessageProcessor[SetChatTopicRequest, ChatTopicChangedEvent, SetChatTopicResponse] {

  import ChatEventMessageProcessor._

  private val RequiredPermission = ChatPermissions.Permissions.SetTopic

  def execute(message: ChatActor.SetChatTopicRequest,
              state: ChatState,
              chatStore: ChatStore,
              permissionsStore: PermissionsStore): ChatEventMessageProcessorResult[SetChatTopicResponse] =
    process(
      message = message,
      state = state,
      checkPermissions = hasPermissions(permissionsStore, message.chatId, RequiredPermission),
      validateMessage = validateMessage,
      createEvent = createEvent,
      persistEvent = persistEvent(chatStore, permissionsStore),
      updateState = updateState,
      createSuccessReply = createSuccessReply,
      createErrorReply = value => ChatActor.SetChatTopicResponse(Left(value))
    )

  def validateMessage(message: SetChatTopicRequest, state: ChatState): Either[ChatActor.SetChatTopicResponse, Unit] = {
    if (!state.members.contains(message.requester)) {
      Left(ChatActor.SetChatTopicResponse(Left(ChatActor.ChatNotJoinedError())))
    } else {
      Right(())
    }
  }

  def createEvent(message: SetChatTopicRequest, state: ChatState): ChatTopicChangedEvent =
    ChatTopicChangedEvent(nextEvent(state), state.id, message.requester, timestamp(), message.topic)

  def persistEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: ChatTopicChangedEvent): Try[Unit] = {
    chatStore.addChatTopicChangedEvent(event)
  }

  def updateState(event: ChatTopicChangedEvent, state: ChatState): ChatState = {
    state.copy(lastEventNumber = event.eventNumber, lastEventTime = event.timestamp, topic = event.topic)
  }

  def createSuccessReply(message: SetChatTopicRequest,
                         event: ChatTopicChangedEvent,
                         state: ChatState): ReplyAndBroadcastTask[SetChatTopicResponse] = {
    replyAndBroadcastTask(
      message.replyTo,
      SetChatTopicResponse(Right(Ok())),
      Some(ChatClientActor.ChatTopicChanged(event.id, event.eventNumber, event.timestamp, event.user, event.topic))
    )
  }

  def createErrorReply(error: CommonErrors): SetChatTopicResponse = {
    ChatActor.SetChatTopicResponse(Left(error))
  }
}
