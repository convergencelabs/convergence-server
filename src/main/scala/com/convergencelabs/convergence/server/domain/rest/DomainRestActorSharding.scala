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

package com.convergencelabs.convergence.server.domain.rest

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityContext}
import com.convergencelabs.convergence.server.ServerClusterRoles
import com.convergencelabs.convergence.server.actor.ActorSharding
import com.convergencelabs.convergence.server.datastore.domain.{DomainPersistenceManager, DomainPersistenceManagerActor}

import scala.concurrent.duration.{Duration, FiniteDuration}

object DomainRestActorSharding {
  private val EntityName = "DomainRestActor"

  def apply(system: ActorSystem[_], sharding: ClusterSharding, numberOfShards: Int): ActorRef[DomainRestActor.Message] = {
    val restSharding = new DomainRestActorSharding(system, sharding, numberOfShards)
    restSharding.shardRegion
  }
}

private class DomainRestActorSharding private(system: ActorSystem[_], sharding: ClusterSharding, numberOfShards: Int)
  extends ActorSharding[DomainRestActor.Message, Props](DomainRestActorSharding.EntityName, ServerClusterRoles.Backend, system, sharding, numberOfShards) {

  def extractEntityId(msg: DomainRestActor.Message): String =
    s"${msg.domainId.namespace}::${msg.domainId.domainId}"

  def createBehavior(props: Props,
                     system: ActorSystem[_],
                     shardRegion: ActorRef[DomainRestActor.Message],
                     entityContext: EntityContext[DomainRestActor.Message]): Behavior[DomainRestActor.Message] = {
    DomainRestActor(shardRegion, entityContext.shard, props.domainPersistenceManager, props.receiveTimeout)
  }

  override protected def createProperties(): Props = {
    val receiveTimeout = Duration.fromNanos(
      system.settings.config.getDuration("convergence.realtime.model.passivation-timeout").toNanos)
    Props(receiveTimeout, DomainPersistenceManagerActor)
  }
}

private case class Props(receiveTimeout: FiniteDuration, domainPersistenceManager: DomainPersistenceManager)
