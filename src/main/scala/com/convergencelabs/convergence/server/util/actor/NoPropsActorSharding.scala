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

import akka.NotUsed
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityContext}

import scala.reflect.ClassTag

abstract class NoPropsActorSharding[T](name: String, systemRole: String, sharding: ClusterSharding, numberOfShards: Int)(implicit t:ClassTag[T])
  extends ActorSharding[T, NotUsed](name, systemRole, sharding, numberOfShards) {

  protected def createProperties(): NotUsed = NotUsed

  protected def createBehavior(createProps: NotUsed, shardRegion: ActorRef[T], entityContext: EntityContext[T]):  Behavior[T] = {
    this.createBehavior(shardRegion, entityContext)
  }

  protected def createBehavior(shardRegion: ActorRef[T], entityContext: EntityContext[T]):  Behavior[T]
}
