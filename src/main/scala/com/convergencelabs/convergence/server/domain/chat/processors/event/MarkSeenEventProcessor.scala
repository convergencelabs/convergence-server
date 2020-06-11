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
import com.convergencelabs.convergence.server.datastore.domain.{ChatMember, ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor._
import com.convergencelabs.convergence.server.domain.chat.ChatPermissionResolver.hasPermissions
import com.convergencelabs.convergence.server.domain.chat.processors.ReplyAndBroadcastTask
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatPermissions, ChatState}

import scala.util.Try

case class MarkSeen(chatId: String, user: DomainUserId, eventNumber: Long)

object MarkSeenEventProcessor extends ChatEventMessageProcessor[MarkChatsEventsSeenRequest, MarkSeen, MarkChatsEventsSeenResponse] {

  private val RequiredPermission = ChatPermissions.Permissions.SetTopic

  def execute(message: ChatActor.MarkChatsEventsSeenRequest,
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
      createErrorReply = value => ChatActor.MarkChatsEventsSeenResponse(Left(value))
    )

  def validateMessage(message: MarkChatsEventsSeenRequest, state: ChatState): Either[ChatActor.MarkChatsEventsSeenResponse, Unit] = {
    if (!state.members.contains(message.requester)) {
      Left(ChatActor.MarkChatsEventsSeenResponse(Left(ChatActor.ChatNotJoinedError())))
    } else {
      Right(())
    }
  }

  def createEvent(message: MarkChatsEventsSeenRequest, state: ChatState): MarkSeen =
    MarkSeen(message.chatId, message.requester, message.eventNumber)

  def processEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: MarkSeen): Try[Unit] = {
    chatStore.markSeen(event.chatId, event.user, event.eventNumber)
  }

  def updateState(event: MarkSeen, state: ChatState): ChatState = {
    val newMembers = state.members + (event.user -> ChatMember(event.chatId, event.user, event.eventNumber))
    state.copy(members = newMembers)
  }

  def createSuccessReply(state: ChatState)(message: MarkChatsEventsSeenRequest, event: MarkSeen): ReplyAndBroadcastTask = {
    replyAndBroadcastTask(
      message.replyTo,
      MarkChatsEventsSeenResponse(Right(())),
      Some(ChatClientActor.EventsMarkedSeen(event.chatId, event.eventNumber, message.requester))
    )
  }

  def createErrorReply(error: CommonErrors): MarkChatsEventsSeenResponse = {
    ChatActor.MarkChatsEventsSeenResponse(Left(error))
  }
}
