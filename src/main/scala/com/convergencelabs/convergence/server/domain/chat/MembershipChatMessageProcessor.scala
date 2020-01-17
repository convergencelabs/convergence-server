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

import akka.actor.{ActorContext, ActorRef}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish

/**
 * Processes messages for a chats that have persistent members.
 *
 * @param stateManager The state manager that controls the persistence of chat state.
 * @param context      The actor context that the ChatActors are deployed into. This
 *                     is used primarily for using the DistributedPubSub mechanism
 *                     for broadcasting messages.
 */
private[chat] abstract class MembershipChatMessageProcessor(stateManager: ChatStateManager,
                                                            context: ActorContext)
  extends ChatMessageProcessor(stateManager) {

  private[this] val mediator: ActorRef = DistributedPubSub(context.system).mediator

  def broadcast(message: Any): Unit = {
    val members = stateManager.state().members
    members.values.foreach { member =>
      val topic = ChatActor.getChatUsernameTopicName(member.userId)
      mediator ! Publish(topic, message)
    }
  }
}
