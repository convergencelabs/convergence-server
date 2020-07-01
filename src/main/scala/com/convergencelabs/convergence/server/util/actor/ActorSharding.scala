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

package com.convergencelabs.convergence.server.actor

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
 */
abstract class ActorSharding[T, P](name: String, systemRole: String, sharding: ClusterSharding, numberOfShards: Int)(implicit t:ClassTag[T]) {

  private[this] var createProperties: Option[P] = None

  val shardRegion: ActorRef[T] =
    sharding.init(
      Entity(EntityTypeKey[T](name))(createBehavior = (entityContext: EntityContext[T]) => createBehaviorWrapper(entityContext))
        .withRole(systemRole)
        .withMessageExtractor(NonWrappedMessageExtractor.create(numberOfShards)(extractEntityId))
    )

  protected def extractEntityId(msg: T): String

  protected def createProperties(): P

  protected def createBehavior(createProps: P, shardRegion: ActorRef[T], entityContext: EntityContext[T]): Behavior[T]

  private[this] def createBehaviorWrapper(entityContext: EntityContext[T]): Behavior[T] = {
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
