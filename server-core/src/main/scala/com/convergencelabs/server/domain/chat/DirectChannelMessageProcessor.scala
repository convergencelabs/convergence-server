package com.convergencelabs.server.domain.chat

import scala.util.Failure
import scala.util.Try

import com.convergencelabs.server.domain.chat.ChatChannelMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.InvalidChannelMessageExcpetion
import com.convergencelabs.server.domain.chat.ChatChannelMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.LeaveChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoveUserFromChannelRequest

import akka.actor.ActorContext

class DirectChatMessageProcessor(
  stateManager: ChatChannelStateManager,
  context: ActorContext)
    extends MembershipChatChannelMessageProcessor(stateManager, context) {

  override def processChatMessage(message: ExistingChannelMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case _: AddUserToChannelRequest =>
        Failure(InvalidChannelMessageExcpetion("Can not add user to a direct channel"))
      case _: RemoveUserFromChannelRequest =>
        Failure(InvalidChannelMessageExcpetion("Can not remove a user from a direct channel"))
      case _: JoinChannelRequest =>
        Failure(InvalidChannelMessageExcpetion("Can not join a direct channel"))
      case _: LeaveChannelRequest =>
        Failure(InvalidChannelMessageExcpetion("Can not leave a direct channel"))
      case _: ExistingChannelMessage =>
        super.processChatMessage(message)
    }
  }
}