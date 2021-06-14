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

package com.convergencelabs.convergence.server.backend.services.domain.activity

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityContext}
import com.convergencelabs.convergence.server.ConvergenceServerConstants.ServerClusterRoles
import com.convergencelabs.convergence.server.util.actor.NoPropsActorSharding

private class ActivityActorSharding(sharding: ClusterSharding, numberOfShards: Int)
  extends NoPropsActorSharding[ActivityActor.Message](ActivityActorSharding.EntityName, ServerClusterRoles.Backend, sharding, numberOfShards) {

   val entityIdSerializer = new ActivityEntityIdSerializer()

  override def extractEntityId(msg: ActivityActor.Message): String =
    entityIdSerializer.serialize((msg.domain, msg.activityId))

  override def createBehavior(shardRegion: ActorRef[ActivityActor.Message],
                              entityContext: EntityContext[ActivityActor.Message]): Behavior[ActivityActor.Message] = {
    val (domainId, activityId) = entityIdSerializer.deserialize(entityContext.entityId)
    ActivityActor(domainId, activityId, shardRegion, entityContext.shard)
  }
}

object ActivityActorSharding  {
  private val EntityName = "Activities"

  def apply(sharding: ClusterSharding, numberOfShards: Int): ActorRef[ActivityActor.Message] = {
    val activitySharding = new ActivityActorSharding(sharding, numberOfShards)
    activitySharding.shardRegion
  }
}
