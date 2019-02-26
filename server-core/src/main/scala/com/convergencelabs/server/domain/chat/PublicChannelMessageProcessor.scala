package com.convergencelabs.server.domain.chat

import akka.actor.ActorContext

class PublicChannelMessageProcessor(
  channelManager: ChatStateManager,
  context: ActorContext)
    extends MembershipChatMessageProcessor(channelManager, context) {
}
