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

package com.convergencelabs.convergence.server.backend.services.domain.chat.processors

import akka.actor.typed.ActorRef
import com.convergencelabs.convergence.server.backend.datastore.domain.chat.ChatStore
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.PermissionsStore
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor._
import com.convergencelabs.convergence.server.backend.services.domain.chat.processors.event.ChatEventMessageProcessorResult
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatDeliveryActor
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.chat.ChatState

/**
 * Processes messages for a Direct Chat.
 *
 * @param chatState        The current state of the chat.
 * @param chatStore        The chat persistence store
 * @param permissionsStore The permissions persistence store.
 */
private[chat] final class DirectChatMessageProcessor(chatState: ChatState,
                                               chatStore: ChatStore,
                                               permissionsStore: PermissionsStore,
                                               domainId: DomainId,
                                               chatDelivery: ActorRef[ChatDeliveryActor.Send])
  extends MembershipChatMessageProcessor(chatState, chatStore, permissionsStore, domainId, chatDelivery) {

  override def onJoinChatRequest(msg: JoinChatRequest): ChatEventMessageProcessorResult[JoinChatResponse] =
    ChatEventMessageProcessorResult(None,
      ReplyAndBroadcastTask(
        MessageReplyTask(msg.replyTo, JoinChatResponse(Left(ChatOperationNotSupported("Can not join a direct chat")))),
        None))

  override def onLeaveChatRequest(msg: LeaveChatRequest): ChatEventMessageProcessorResult[LeaveChatResponse] =
    ChatEventMessageProcessorResult(None,
      ReplyAndBroadcastTask(
        MessageReplyTask(msg.replyTo, LeaveChatResponse(Left(ChatOperationNotSupported("Can not leave a direct chat")))),
        None))

  override def onAddUserToChatRequest(msg: AddUserToChatRequest): ChatEventMessageProcessorResult[AddUserToChatResponse] =
    ChatEventMessageProcessorResult(None,
      ReplyAndBroadcastTask(
        MessageReplyTask(msg.replyTo, AddUserToChatResponse(Left(ChatOperationNotSupported("Can not add user to a direct chat")))),
        None))

  override def onRemovedUserFromChatRequest(msg: RemoveUserFromChatRequest): ChatEventMessageProcessorResult[RemoveUserFromChatResponse] =
    ChatEventMessageProcessorResult(None,
      ReplyAndBroadcastTask(
        MessageReplyTask(msg.replyTo, RemoveUserFromChatResponse(Left(ChatOperationNotSupported("Can not remove user to a direct chat")))),
        None))

}
