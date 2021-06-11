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

package com.convergencelabs.convergence.server.util.actor

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityContext, EntityTypeKey}

import scala.reflect.ClassTag

/**
 * A utility base class that contains all of the needed functions to produce
 * and consume a Sharded Akka Actor.
 *
 * @param name           The name of the Actor.
 * @param systemRole     The Actor System Cluster Role on which to start the shard
 *                       regions.
 * @param numberOfShards The number of shards to create across the cluster.
 *
 * @tparam M The type of message this actor receives.
 * @tparam P The props this actor takes.
 */
abstract class ActorSharding[M, P](name: String, systemRole: String, sharding: ClusterSharding, numberOfShards: Int)(implicit t:ClassTag[M]) {

  private[this] var createProperties: Option[P] = None

  val shardRegion: ActorRef[M] =
    sharding.init(
      Entity(EntityTypeKey[M](name))(createBehavior = (entityContext: EntityContext[M]) => createBehaviorWrapper(entityContext))
        .withRole(systemRole)
        .withMessageExtractor(NonWrappedMessageExtractor.create(numberOfShards)(extractEntityId))
    )

  protected def extractEntityId(msg: M): String

  protected def createProperties(): P

  protected def createBehavior(createProps: P, shardRegion: ActorRef[M], entityContext: EntityContext[M]): Behavior[M]

  private[this] def createBehaviorWrapper(entityContext: EntityContext[M]): Behavior[M] = {
    val props = createProperties match {
      case Some(props) =>
        props
      case None =>
        val p = createProperties()
        createProperties = Some(p)
        p
    }

    createBehavior(props, shardRegion, entityContext)
  }
}
