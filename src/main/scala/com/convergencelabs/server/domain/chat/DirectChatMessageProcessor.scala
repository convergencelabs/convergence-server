/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.chat

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.domain.chat.ChatMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.ExistingChatMessage
import com.convergencelabs.server.domain.chat.ChatMessages.InvalidChatMessageExcpetion
import com.convergencelabs.server.domain.chat.ChatMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.LeaveChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.RemoveUserFromChannelRequest

import akka.actor.ActorContext

class DirectChatMessageProcessor(
  stateManager: ChatStateManager,
  context: ActorContext)
    extends MembershipChatMessageProcessor(stateManager, context) {

  override def processChatMessage(message: ExistingChatMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case _: AddUserToChannelRequest =>
        Failure(InvalidChatMessageExcpetion("Can not add user to a direct channel"))
      case _: RemoveUserFromChannelRequest =>
        Failure(InvalidChatMessageExcpetion("Can not remove a user from a direct channel"))
      case _: JoinChannelRequest =>
        Failure(InvalidChatMessageExcpetion("Can not join a direct channel"))
      case _: LeaveChannelRequest =>
        Failure(InvalidChatMessageExcpetion("Can not leave a direct channel"))
      case _: ExistingChatMessage =>
        super.processChatMessage(message)
    }
  }
}