package com.convergencelabs.server.domain.chat

import akka.actor.ActorContext
import com.convergencelabs.server.domain.chat.ChatChannelMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoveUserFromChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ExistingChannelMessage
import scala.util.Try
import com.convergencelabs.server.domain.chat.ChatChannelMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.LeaveChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.InvalidChannelMessageExcpetion
import scala.util.Failure
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelNotFoundException

class PrivateChannelMessageProcessor(
  channelManager: ChatChannelStateManager,
  context: ActorContext)
    extends MembershipChatChannelMessageProcessor(channelManager, context) {

  override def processChatMessage(message: ExistingChannelMessage): Try[ChatMessageProcessingResult] = {
    message match {
      case message: AddUserToChannelRequest =>
        val state = channelManager.state()
        if (state.members.contains(message.addedBy)) {
          super.processChatMessage(message)
        } else {
          Failure(ChannelNotFoundException(state.id))
        }
      case message: RemoveUserFromChannelRequest =>
        val state = channelManager.state()
        if (state.members.contains(message.removedBy)) {
          super.processChatMessage(message)
        } else {
          Failure(ChannelNotFoundException(state.id))
        }
      case _: JoinChannelRequest =>
        Failure(InvalidChannelMessageExcpetion("Can not join a private channel"))
      case message: ExistingChannelMessage =>
        super.processChatMessage(message)
    }
  }
}
