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

package com.convergencelabs.convergence.server.domain.chat.processors

import akka.actor.typed.ActorRef
import com.convergencelabs.convergence.server.datastore.domain.{ChatStore, PermissionsStore}
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.chat.ChatActor._
import com.convergencelabs.convergence.server.domain.chat.processors.event.ChatEventMessageProcessorResult
import com.convergencelabs.convergence.server.domain.chat.{ChatDeliveryActor, ChatState}

/**
 * Processes messages for a Private Chats. The main things this cl
 *
 * @param chatState        The current state of the chat.
 * @param chatStore        The chat persistence store
 * @param permissionsStore The permissions persistence store.
 */
private[chat] class PrivateChannelMessageProcessor(chatState: ChatState,
                                                   chatStore: ChatStore,
                                                   permissionsStore: PermissionsStore,
                                                   domainId: DomainId,
                                                   chatDelivery: ActorRef[ChatDeliveryActor.Send])
  extends MembershipChatMessageProcessor(chatState, chatStore, permissionsStore, domainId, chatDelivery) {


  override def onJoinChatRequest(msg: JoinChatRequest): ChatEventMessageProcessorResult[JoinChatResponse] = {
    ChatEventMessageProcessorResult(
      None,
      ReplyAndBroadcastTask(
        MessageReplyTask(msg.replyTo, JoinChatResponse(Left(ChatOperationNotSupported("Can not join a private channel")))),
        None
      )
    )
  }

  override def onAddUserToChatRequest(msg: AddUserToChatRequest): ChatEventMessageProcessorResult[AddUserToChatResponse] = {
    if (state.members.contains(msg.requester)) {
      super.onAddUserToChatRequest(msg)
    } else {
      ChatEventMessageProcessorResult(
        None,
        ReplyAndBroadcastTask(
          MessageReplyTask(msg.replyTo, AddUserToChatResponse(Left(ChatNotFoundError()))),
          None
        )
      )
    }
  }

  override def onRemovedUserFromChatRequest(msg: RemoveUserFromChatRequest): ChatEventMessageProcessorResult[RemoveUserFromChatResponse] = {
    if (state.members.contains(msg.requester)) {
      super.onRemovedUserFromChatRequest(msg)
    } else {
      ChatEventMessageProcessorResult(
        None,
        ReplyAndBroadcastTask(
          MessageReplyTask(msg.replyTo, RemoveUserFromChatResponse(Left(ChatNotFoundError()))),
          None
        )
      )
    }
  }
}
