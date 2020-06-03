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

package com.convergencelabs.convergence.server.domain.activity

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityContext}
import com.convergencelabs.convergence.server.ServerClusterRoles
import com.convergencelabs.convergence.server.actor.NoPropsActorSharding

object ActivityActorSharding  {
  private val EntityName = "Activities"

  def apply(system: ActorSystem[_], sharding: ClusterSharding, numberOfShards: Int): ActorRef[ActivityActor.Message] = {
    val activitySharding = new ActivityActorSharding(system, sharding, numberOfShards)
    activitySharding.shardRegion
  }
}

private class ActivityActorSharding(system: ActorSystem[_], sharding: ClusterSharding, numberOfShards: Int)
  extends NoPropsActorSharding[ActivityActor.Message](ActivityActorSharding.EntityName, ServerClusterRoles.Backend, system, sharding, numberOfShards) {

  override def extractEntityId(msg: ActivityActor.Message): String =
    s"${msg.domain.namespace}::${msg.domain.namespace}::${msg.activityId}"

  override def createBehavior(system: ActorSystem[_],
                              shardRegion: ActorRef[ActivityActor.Message],
                              entityContext: EntityContext[ActivityActor.Message]): Behavior[ActivityActor.Message] = {
    ActivityActor(shardRegion, entityContext.shard)
  }
}
