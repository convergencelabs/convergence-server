package com.convergencelabs.server.domain.chat

import akka.actor.ActorContext

class PublicChannelMessageProcessor(
  channelManager: ChatChannelStateManager,
  context: ActorContext)
    extends MembershipChatChannelMessageProcessor(channelManager, context) {
}
