/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.chat

import akka.actor.ActorContext

class PublicChannelMessageProcessor(
  channelManager: ChatStateManager,
  context: ActorContext)
    extends MembershipChatMessageProcessor(channelManager, context) {
}
