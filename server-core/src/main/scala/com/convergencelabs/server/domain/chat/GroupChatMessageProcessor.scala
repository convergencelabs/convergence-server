package com.convergencelabs.server.domain.chat

import akka.actor.ActorContext

class GroupChatMessageProcessor(
  channelManager: ChatChannelStateManager,
  context: ActorContext)
    extends MembershipChatChannelMessageProcessor(channelManager, context) {
}
