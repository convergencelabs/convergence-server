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
import com.convergencelabs.convergence.server.datastore.domain.{ChatMessageEvent, ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.chat.ChatActor.{CommonErrors, PublishChatMessageAck, PublishChatMessageRequest, PublishChatMessageResponse}
import com.convergencelabs.convergence.server.domain.chat.processors.ReplyAndBroadcastTask
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatState}

import scala.util.{Success, Try}

/**
 * The [[PublishMessageEventProcessor]] provides helper methods to process
 * the [[PublishChatMessageRequest]].
 */
private[chat] object PublishMessageEventProcessor
  extends ChatEventMessageProcessor[PublishChatMessageRequest, ChatMessageEvent, PublishChatMessageResponse] {

  import ChatEventMessageProcessor._

  def execute(message: ChatActor.PublishChatMessageRequest,
              state: ChatState,
              chatStore: ChatStore,
              permissionsStore: PermissionsStore): ChatEventMessageProcessorResult[PublishChatMessageResponse] =
    process(
      message = message,
      state = state,
      checkPermissions = (_: DomainUserId) => Success(true),
      validateMessage = validateMessage,
      createEvent = createEvent,
      persistEvent = persistEvent(chatStore, permissionsStore),
      updateState = updateState,
      createSuccessReply = createSuccessReply,
      createErrorReply = value => ChatActor.PublishChatMessageResponse(Left(value))
    )

  def validateMessage(message: PublishChatMessageRequest, state: ChatState): Either[ChatActor.PublishChatMessageResponse, Unit] = {
    if (!state.members.contains(message.requester)) {
      Left(ChatActor.PublishChatMessageResponse(Left(ChatActor.ChatNotJoinedError())))
    } else {
      Right(())
    }
  }

  def createEvent(message: PublishChatMessageRequest, state: ChatState): ChatMessageEvent =
    ChatMessageEvent(nextEvent(state), state.id, message.requester, timestamp(), message.message)

  def persistEvent(chatStore: ChatStore, permissionsStore: PermissionsStore)(event: ChatMessageEvent): Try[Unit] = {
    chatStore.addChatMessageEvent(event)
  }

  def updateState(event: ChatMessageEvent, state: ChatState): ChatState = {
    state.copy(lastEventNumber = event.eventNumber, lastEventTime = event.timestamp)
  }

  def createSuccessReply(message: PublishChatMessageRequest,
                         event: ChatMessageEvent,
                         state: ChatState): ReplyAndBroadcastTask[PublishChatMessageResponse] = {
    replyAndBroadcastTask(
      message.replyTo,
      PublishChatMessageResponse(Right(PublishChatMessageAck(event.eventNumber, event.timestamp))),
      Some(ChatClientActor.RemoteChatMessage(event.id, event.eventNumber, event.timestamp, message.requester, event.message))
    )
  }

  def createErrorReply(error: CommonErrors): PublishChatMessageResponse = {
    ChatActor.PublishChatMessageResponse(Left(error))
  }
}
