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

package com.convergencelabs.convergence.server.backend.services.domain

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityContext}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.util.DomainIdEntityIdSerializer
import com.convergencelabs.convergence.server.util.actor.ActorSharding

import scala.reflect.ClassTag

abstract class DomainIdBasedActorSharding[M, P](name: String,
                                                systemRole: String,
                                                sharding: ClusterSharding,
                                                numberOfShards: Int)(implicit t: ClassTag[M])
  extends ActorSharding[M, P](name, systemRole, sharding, numberOfShards) {

  private val entityIdSerializer = new DomainIdEntityIdSerializer()

  override def extractEntityId(msg: M): String = {
    val domainId = getDomainId(msg)
    entityIdSerializer.serialize(domainId)
  }

  override def createBehavior(props: P,
                              shardRegion: ActorRef[M],
                              entityContext: EntityContext[M]): Behavior[M] = {
    val domainId = entityIdSerializer.deserialize(entityContext.entityId)
    createBehavior(domainId, props, shardRegion, entityContext)
  }

  protected def getDomainId(m: M): DomainId

  protected def createBehavior(domainId: DomainId,
                               props: P,
                               shardRegion: ActorRef[M],
                               entityContext: EntityContext[M]): Behavior[M]

}


