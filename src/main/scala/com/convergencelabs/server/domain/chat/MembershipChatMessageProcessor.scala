/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.chat

import akka.cluster.pubsub.DistributedPubSub
import akka.actor.ActorContext
import akka.cluster.pubsub.DistributedPubSubMediator.Publish

abstract class MembershipChatMessageProcessor(
  stateManager: ChatStateManager,
  context: ActorContext)
    extends ChatMessageProcessor(stateManager) {
  val mediator = DistributedPubSub(context.system).mediator

  def boradcast(message: Any): Unit = {
    val members = stateManager.state().members
    members.values.foreach { member =>
      val topic = ChatActor.getChatUsernameTopicName(member.userId)
      mediator ! Publish(topic, message)
    }
  }
}
