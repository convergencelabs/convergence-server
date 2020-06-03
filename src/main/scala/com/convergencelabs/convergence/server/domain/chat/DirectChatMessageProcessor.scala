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

import akka.actor.typed.scaladsl.ActorContext
import com.convergencelabs.convergence.server.domain.chat.ChatActor._

import scala.util.{Failure, Try}

/**
 * Processes messages for a Direct Chat.
 *
 * @param stateManager The state manager that controls the persistence of chat state.
 * @param context      The actor context that the ChatActors are deployed into.
 */
private[chat] class DirectChatMessageProcessor(stateManager: ChatStateManager,
                                               context: ActorContext[_])
  extends MembershipChatMessageProcessor(stateManager, context) {

  override def processChatMessage(message: RequestMessage): Try[ChatMessageProcessingResult[_]] = {
    message match {
      case _: AddUserToChatRequest =>
        Failure(InvalidChatMessageException("Can not add user to a direct channel"))
      case _: RemoveUserFromChatRequest =>
        Failure(InvalidChatMessageException("Can not remove a user from a direct channel"))
      case _: JoinChatRequest =>
        Failure(InvalidChatMessageException("Can not join a direct channel"))
      case _: LeaveChatRequest =>
        Failure(InvalidChatMessageException("Can not leave a direct channel"))
      case _: Message =>
        super.processChatMessage(message)
    }
  }
}