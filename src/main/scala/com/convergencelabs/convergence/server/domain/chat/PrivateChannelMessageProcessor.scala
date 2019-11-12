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

package com.convergencelabs.convergence.server.domain.chat

import akka.actor.ActorContext
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.AddUserToChannelRequest
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.RemoveUserFromChannelRequest
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.ExistingChatMessage
import scala.util.Try
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.JoinChannelRequest
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.LeaveChannelRequest
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.InvalidChatMessageExcpetion
import scala.util.Failure
import com.convergencelabs.convergence.server.domain.chat.ChatMessages.ChatNotFoundException

class PrivateChannelMessageProcessor(
  channelManager: ChatStateManager,
  context: ActorContext)
    extends MembershipChatMessageProcessor(channelManager, context) {

  override def processChatMessage(message: ExistingChatMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case message: AddUserToChannelRequest =>
        val state = channelManager.state()
        if (state.members.contains(message.requestor.userId)) {
          super.processChatMessage(message)
        } else {
          Failure(ChatNotFoundException(state.id))
        }
      case message: RemoveUserFromChannelRequest =>
        val state = channelManager.state()
        if (state.members.contains(message.requestor.userId)) {
          super.processChatMessage(message)
        } else {
          Failure(ChatNotFoundException(state.id))
        }
      case _: JoinChannelRequest =>
        Failure(InvalidChatMessageExcpetion("Can not join a private channel"))
      case message: ExistingChatMessage =>
        super.processChatMessage(message)
    }
  }
}
