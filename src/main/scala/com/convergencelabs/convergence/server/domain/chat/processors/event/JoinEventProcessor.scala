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
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{CommonErrors, JoinChatRequest, JoinChatResponse}
import com.convergencelabs.convergence.server.domain.chat.ChatPermissionResolver.hasPermissions
import com.convergencelabs.convergence.server.domain.chat.processors.{MessageReplyTask, ReplyAndBroadcastTask}
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatPermissions, ChatState}

import scala.util.Try

object JoinEventProcessor extends ChatEventMessageProcessor[JoinChatRequest, ChatUserJoinedEvent, JoinChatResponse] {
  import ChatEventMessageProcessor._

  private val RequiredPermission = ChatPermissions.Permissions.JoinChat

  def execute(message: ChatActor.JoinChatRequest,
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
      createErrorReply = value => ChatActor.JoinChatResponse(Left(value))
    )

  def validateMessage(message: JoinChatRequest, state: ChatState): Either[ChatActor.JoinChatResponse, Unit] = {
    if (state.members.contains(message.requester)) {
      Left(ChatActor.JoinChatResponse(Left(ChatActor.ChatAlreadyJoinedError())))
    } else {
      Right(())
    }
  }

  def createEvent(message: JoinChatRequest, state: ChatState): ChatUserJoinedEvent =
    ChatUserJoinedEvent(nextEvent(state), state.id, message.requester, timestamp())

  def processEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: ChatUserJoinedEvent): Try[Unit] = {
    chatStore.addChatUserJoinedEvent(event)
  }

  def updateState(event: ChatUserJoinedEvent, state: ChatState): ChatState = {
    val newMembers = state.members + (event.user -> ChatMember(event.id, event.user, 0))
    state.copy(lastEventNumber = event.eventNumber, lastEventTime = event.timestamp, members = newMembers)
  }

  def createSuccessReply(message: JoinChatRequest, event: ChatUserJoinedEvent, state: ChatState): ReplyAndBroadcastTask = {
    val info = stateToInfo(state)
    ReplyAndBroadcastTask(
      MessageReplyTask(message.replyTo, JoinChatResponse(Right(info))),
      Some(ChatClientActor.UserJoinedChat(event.id, event.eventNumber, event.timestamp, event.user))
    )
  }

  def createErrorReply(error: CommonErrors): JoinChatResponse = {
    ChatActor.JoinChatResponse(Left(error))
  }

  def stateToInfo(state: ChatState): ChatInfo = {
    val ChatState(id, chatType, created, isPrivate, name, topic, lastEventTime, lastEventNo, members) = state
    ChatInfo(id, chatType, created, isPrivate, name, topic, lastEventNo, lastEventTime, members.values.toSet)
  }
}
