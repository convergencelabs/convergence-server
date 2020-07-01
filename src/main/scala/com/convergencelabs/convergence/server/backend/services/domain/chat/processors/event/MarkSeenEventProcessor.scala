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
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.api.realtime.ChatClientActor
import com.convergencelabs.convergence.server.backend.datastore.domain.chat.ChatStore
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.PermissionsStore
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor._
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatPermissionResolver.hasPermissions
import com.convergencelabs.convergence.server.backend.services.domain.chat.processors.ReplyAndBroadcastTask
import com.convergencelabs.convergence.server.backend.services.domain.chat.{ChatActor, ChatPermissions, ChatState}
import com.convergencelabs.convergence.server.model.domain.chat.ChatMember

import scala.util.Try

/**
 * The [[MarkSeenEventProcessor]] provides helper methods to process
 * the [[MarkChatsEventsSeenRequest]]. It should be noted that this
 * is somewhat of a special case where the mark seen event does not
 * generate a chat event that needs to be persisted to the chat store
 * rather this event merely updates the particular chat members
 * last seen event.  This is the reason for the [[MarkSeen]]
 * case class rather than using a chat store event object.
 */
private[chat] object MarkSeenEventProcessor
  extends ChatEventMessageProcessor[MarkChatsEventsSeenRequest, MarkSeen, MarkChatsEventsSeenResponse] {

  import ChatEventMessageProcessor._


  private val RequiredPermission = ChatPermissions.Permissions.SetTopic

  def execute(message: ChatActor.MarkChatsEventsSeenRequest,
              state: ChatState,
              chatStore: ChatStore,
              permissionsStore: PermissionsStore): ChatEventMessageProcessorResult[MarkChatsEventsSeenResponse] =
    process(
      message = message,
      state = state,
      checkPermissions = hasPermissions(permissionsStore, message.chatId, RequiredPermission),
      validateMessage = validateMessage,
      createEvent = createEvent,
      persistEvent = persistEvent(chatStore, permissionsStore),
      updateState = updateState,
      createSuccessReply = createSuccessReply,
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

  def persistEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: MarkSeen): Try[Unit] = {
    chatStore.markSeen(event.chatId, event.user, event.eventNumber)
  }

  def updateState(event: MarkSeen, state: ChatState): ChatState = {
    val newMembers = state.members + (event.user -> ChatMember(event.chatId, event.user, event.eventNumber))
    state.copy(members = newMembers)
  }

  def createSuccessReply(message: MarkChatsEventsSeenRequest,
                         event: MarkSeen,
                         state: ChatState): ReplyAndBroadcastTask[MarkChatsEventsSeenResponse] = {
    replyAndBroadcastTask(
      message.replyTo,
      MarkChatsEventsSeenResponse(Right(Ok())),
      Some(ChatClientActor.EventsMarkedSeen(event.chatId, message.requester,  event.eventNumber))
    )
  }

  def createErrorReply(error: CommonErrors): MarkChatsEventsSeenResponse = {
    ChatActor.MarkChatsEventsSeenResponse(Left(error))
  }
}

/**
 * A helper case class that defines the data that needs to be passed from the
 * create event to the persistEvent method, since we don't have a concrete
 * chat event class for this message type.
 *
 * @param chatId      The id of the chat.
 * @param user        The user id of the chat member
 * @param eventNumber The latest event for the chat the user has seen.
 */
private[chat] final case class MarkSeen(chatId: String, user: DomainUserId, eventNumber: Long)