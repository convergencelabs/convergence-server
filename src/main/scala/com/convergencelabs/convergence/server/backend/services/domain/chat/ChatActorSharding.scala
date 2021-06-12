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
import com.convergencelabs.convergence.server.util.DomainAndStringEntityIdSerializer
import com.convergencelabs.convergence.server.util.actor.NoPropsActorSharding
import com.typesafe.config.Config

import scala.concurrent.duration.Duration

object ChatActorSharding  {
  private val EntityName = "Chat"

  def apply(config: Config,
            sharding: ClusterSharding,
            numberOfShards: Int,
            chatDeliveryRegion: ActorRef[ChatDeliveryActor.Send]): ActorRef[ChatActor.Message] = {
    val chatSharding = new ChatActorSharding(config, sharding, numberOfShards, chatDeliveryRegion)
    chatSharding.shardRegion
  }
}

/**
 * Configures the sharding of the [[ChatActor]].
 */
private class ChatActorSharding(config: Config, sharding: ClusterSharding, numberOfShards: Int, chatDeliveryRegion: ActorRef[ChatDeliveryActor.Send])
  extends NoPropsActorSharding[ChatActor.Message](ChatActorSharding.EntityName, ServerClusterRoles.Backend, sharding, numberOfShards) {

  private val entityIdSerializer = new DomainAndStringEntityIdSerializer()

  def extractEntityId(msg: ChatActor.Message): String =
    entityIdSerializer.serialize(msg.domainId, msg.chatId)


  def createBehavior(shardRegion: ActorRef[ChatActor.Message],
                     entityContext: EntityContext[ChatActor.Message]): Behavior[ChatActor.Message] = {

    val receiveTimeout = Duration.fromNanos(
      config.getDuration("convergence.realtime.chat.passivation-timeout").toNanos)

    val (domainId, chatId) = entityIdSerializer.deserialize(entityContext.entityId)

    ChatActor(
      domainId,
      chatId,
      shardRegion,
      entityContext.shard,
      chatDeliveryRegion,
      receiveTimeout)
  }
}

