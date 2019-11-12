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
