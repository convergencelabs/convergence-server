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

package com.convergencelabs.convergence.server.backend.services.domain.chat

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityContext}
import com.convergencelabs.convergence.server.ConvergenceServerConstants.ServerClusterRoles
import com.convergencelabs.convergence.server.actor.NoPropsActorSharding

object ChatActorSharding  {
  private val EntityName = "Chat"

  def apply(sharding: ClusterSharding,
            numberOfShards: Int,
            chatDeliveryRegion: ActorRef[ChatDeliveryActor.Send]): ActorRef[ChatActor.Message] = {
    val chatSharding = new ChatActorSharding(sharding, numberOfShards, chatDeliveryRegion)
    chatSharding.shardRegion
  }
}

/**
 * Configures the sharding of the [[ChatActor]].
 */
private class ChatActorSharding(sharding: ClusterSharding, numberOfShards: Int, chatDeliveryRegion: ActorRef[ChatDeliveryActor.Send])
  extends NoPropsActorSharding[ChatActor.Message](ChatActorSharding.EntityName, ServerClusterRoles.Backend, sharding, numberOfShards) {

  def extractEntityId(msg: ChatActor.Message): String =
    s"${msg.domainId.namespace}::${msg.domainId.domainId}::${msg.chatId}"

  def createBehavior(shardRegion: ActorRef[ChatActor.Message],
                     entityContext: EntityContext[ChatActor.Message]): Behavior[ChatActor.Message] = {
    ChatActor(shardRegion, entityContext.shard, chatDeliveryRegion)
  }
}
