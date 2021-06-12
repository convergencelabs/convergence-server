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
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatDeliveryActorSharding.UserEntityIdSerializer
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.util.EntityIdSerializer
import com.convergencelabs.convergence.server.util.actor.NoPropsActorSharding

/**
 * Configures the sharding of the [[ChatDeliveryActor]].
 */
private final class ChatDeliveryActorSharding(sharding: ClusterSharding, numberOfShards: Int)
  extends NoPropsActorSharding[ChatDeliveryActor.Message](ChatDeliveryActorSharding.EntityName, ServerClusterRoles.Backend, sharding, numberOfShards) {

  private val entityIdSerializer = new UserEntityIdSerializer()

  def extractEntityId(msg: ChatDeliveryActor.Message): String =
    entityIdSerializer.serialize((msg.domainId, msg.userId))

  def createBehavior(shardRegion: ActorRef[ChatDeliveryActor.Message],
                     entityContext: EntityContext[ChatDeliveryActor.Message]): Behavior[ChatDeliveryActor.Message] = {

    val (domainId, userId) = entityIdSerializer.deserialize(entityContext.entityId)
    ChatDeliveryActor(domainId, userId, shardRegion, entityContext.shard)
  }
}

object ChatDeliveryActorSharding  {
  private val EntityName = "ChatDelivery"

  def apply(sharding: ClusterSharding, numberOfShards: Int): ActorRef[ChatDeliveryActor.Message] = {
    val chatSharding = new ChatDeliveryActorSharding(sharding, numberOfShards)
    chatSharding.shardRegion
  }

  class UserEntityIdSerializer extends EntityIdSerializer[(DomainId, DomainUserId)] {
    override protected def entityIdToParts(entityId: (DomainId, DomainUserId)): List[String] =
      List(entityId._1.domainId, entityId._1.domainId, entityId._2.userType.toString, entityId._2.username)

    override protected def partsToEntityId(parts: List[String]): (DomainId, DomainUserId) =
      (DomainId(parts.head, parts(1)), DomainUserId(DomainUserType.withName(parts(2)), parts(3)))
  }
}
