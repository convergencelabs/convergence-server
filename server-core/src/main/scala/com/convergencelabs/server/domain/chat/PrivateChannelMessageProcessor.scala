package com.convergencelabs.server.domain.chat

import akka.actor.ActorContext
import com.convergencelabs.server.domain.chat.ChatMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.RemoveUserFromChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.ExistingChatMessage
import scala.util.Try
import com.convergencelabs.server.domain.chat.ChatMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.LeaveChannelRequest
import com.convergencelabs.server.domain.chat.ChatMessages.InvalidChatMessageExcpetion
import scala.util.Failure
import com.convergencelabs.server.domain.chat.ChatMessages.ChatNotFoundException

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
