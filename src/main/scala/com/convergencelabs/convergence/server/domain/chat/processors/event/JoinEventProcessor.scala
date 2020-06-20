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
import com.convergencelabs.convergence.server.datastore.domain.PermissionsStore.ChatPermissionTarget
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{CommonErrors, JoinChatRequest, JoinChatResponse}
import com.convergencelabs.convergence.server.domain.chat.ChatPermissionResolver.hasPermissions
import com.convergencelabs.convergence.server.domain.chat.processors.{MessageReplyTask, ReplyAndBroadcastTask}
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatPermissions, ChatState}

import scala.util.{Success, Try}

/**
 * The [[JoinEventProcessor]] provides helper methods to process
 * the [[JoinChatRequest]].
 */
private[chat] object JoinEventProcessor
  extends ChatEventMessageProcessor[JoinChatRequest, ChatUserJoinedEvent, JoinChatResponse] {

  import ChatEventMessageProcessor._

  private val RequiredPermission = ChatPermissions.Permissions.JoinChat

  def execute(message: ChatActor.JoinChatRequest,
              state: ChatState,
              chatStore: ChatStore,
              permissionsStore: PermissionsStore): ChatEventMessageProcessorResult[JoinChatResponse] =
    process(
      message = message,
      state = state,
      checkPermissions = hasPermission(state, chatStore, permissionsStore),
      validateMessage = validateMessage,
      createEvent = createEvent,
      persistEvent = persistEvent(chatStore, permissionsStore),
      updateState = updateState,
      createSuccessReply = createSuccessReply,
      createErrorReply = value => ChatActor.JoinChatResponse(Left(value))
    )

  def hasPermission(state: ChatState,
                    chatStore: ChatStore,
                    permissionsStore: PermissionsStore)(userId: DomainUserId): Try[Boolean] = {
    if (state.membership == ChatMembership.Public) {
      Success(true)
    } else {
      hasPermissions(permissionsStore, state.id, RequiredPermission)(userId)
    }
  }

  def validateMessage(message: JoinChatRequest, state: ChatState): Either[ChatActor.JoinChatResponse, Unit] = {
    if (state.members.contains(message.requester)) {
      Left(ChatActor.JoinChatResponse(Left(ChatActor.ChatAlreadyJoinedError())))
    } else {
      Right(())
    }
  }

  def createEvent(message: JoinChatRequest, state: ChatState): ChatUserJoinedEvent =
    ChatUserJoinedEvent(nextEvent(state), state.id, message.requester, timestamp())

  def persistEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: ChatUserJoinedEvent): Try[Unit] = {
    for {
      _ <- chatStore.addChatUserJoinedEvent(event)
      _ <- permissionsStore.addPermissionsForUser(ChatPermissions.DefaultChatPermissions, event.user, ChatPermissionTarget(event.id))
    } yield ()
  }

  def updateState(event: ChatUserJoinedEvent, state: ChatState): ChatState = {
    val newMembers = state.members + (event.user -> ChatMember(event.id, event.user, 0))
    state.copy(lastEventNumber = event.eventNumber, lastEventTime = event.timestamp, members = newMembers)
  }

  def createSuccessReply(message: JoinChatRequest,
                         event: ChatUserJoinedEvent,
                         state: ChatState): ReplyAndBroadcastTask[JoinChatResponse] = {
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
    val ChatState(id, chatType, created, membership, name, topic, lastEventTime, lastEventNo, members) = state
    ChatInfo(id, chatType, created, membership, name, topic, lastEventNo, lastEventTime, members.values.toSet)
  }
}