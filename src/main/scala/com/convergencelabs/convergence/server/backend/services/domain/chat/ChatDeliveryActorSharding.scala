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

object ChatDeliveryActorSharding  {
  private val EntityName = "ChatDelivery"

  def apply(sharding: ClusterSharding, numberOfShards: Int): ActorRef[ChatDeliveryActor.Message] = {
    val chatSharding = new ChatDeliveryActorSharding(sharding, numberOfShards)
    chatSharding.shardRegion
  }
}
/**
 * Configures the sharding of the [[ChatDeliveryActor]].
 */
private class ChatDeliveryActorSharding(sharding: ClusterSharding, numberOfShards: Int)
  extends NoPropsActorSharding[ChatDeliveryActor.Message](ChatDeliveryActorSharding.EntityName, ServerClusterRoles.Backend, sharding, numberOfShards) {

  def extractEntityId(msg: ChatDeliveryActor.Message): String =
    s"${msg.domainId.namespace}::${msg.domainId.domainId}::${msg.userId}"

  def createBehavior(shardRegion: ActorRef[ChatDeliveryActor.Message],
                     entityContext: EntityContext[ChatDeliveryActor.Message]): Behavior[ChatDeliveryActor.Message] = {
    ChatDeliveryActor(shardRegion, entityContext.shard)
  }
}
