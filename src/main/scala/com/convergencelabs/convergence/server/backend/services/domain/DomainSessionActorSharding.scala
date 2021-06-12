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
import com.convergencelabs.convergence.server.ConvergenceServerConstants.ServerClusterRoles
import com.convergencelabs.convergence.server.backend.services.server.DomainLifecycleTopic
import com.convergencelabs.convergence.server.model.DomainId
import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}

private final class DomainSessionActorSharding(config: Config,
                                               sharding: ClusterSharding,
                                               numberOfShards: Int,
                                               domainLifecycleTopic: () => ActorRef[DomainLifecycleTopic.TopicMessage])
  extends DomainIdBasedActorSharding[DomainSessionActor.Message, DomainSessionActorSharding.Props](
    DomainSessionActorSharding.EntityName, ServerClusterRoles.Backend, sharding, numberOfShards) {

  import DomainSessionActorSharding._

  override def createBehavior(domainId: DomainId,
                              props: Props,
                              shardRegion: ActorRef[DomainSessionActor.Message],
                              entityContext: EntityContext[DomainSessionActor.Message]): Behavior[DomainSessionActor.Message] = {
    val Props(domainPersistenceManager, domainPassivationTimeout, domainLifecycleTopic) = props

    DomainSessionActor(
      domainId,
      shardRegion,
      entityContext.shard,
      domainPersistenceManager,
      domainPassivationTimeout,
      domainLifecycleTopic)
  }

  override protected def createProperties(): Props = {
    val domainPassivationTimeout = Duration.fromNanos(
      config.getDuration("convergence.realtime.domain.passivation-timeout").toNanos)

    Props(DomainPersistenceManagerActor,
      domainPassivationTimeout,
      domainLifecycleTopic())
  }

  override protected def getDomainId(m: DomainSessionActor.Message): DomainId = m.domainId
}

object DomainSessionActorSharding {
  private val EntityName = "DomainActor"

  def apply(config: Config,
            sharding: ClusterSharding,
            numberOfShards: Int,
            domainLifecycleTopic: () => ActorRef[DomainLifecycleTopic.TopicMessage]): ActorRef[DomainSessionActor.Message] = {
    val domainSharding = new DomainSessionActorSharding(config, sharding, numberOfShards, domainLifecycleTopic)
    domainSharding.shardRegion
  }


  final case class Props(domainPersistenceManager: DomainPersistenceManager,
                         domainPassivationTimeout: FiniteDuration,
                         domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])

}
