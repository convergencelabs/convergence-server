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

package com.convergencelabs.convergence.server.backend.services.domain.model

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityContext}
import com.convergencelabs.convergence.server.ConvergenceServerConstants.ServerClusterRoles
import com.convergencelabs.convergence.server.backend.services.domain.{DomainIdBasedActorSharding, DomainPersistenceManager, DomainPersistenceManagerActor}
import com.convergencelabs.convergence.server.model.DomainId
import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}

private final class ModelOperationServiceActorSharding(config: Config, sharding: ClusterSharding, numberOfShards: Int)
  extends DomainIdBasedActorSharding[ModelOperationServiceActor.Message, ModelOperationServiceActorSharding.Props](
    ModelOperationServiceActorSharding.EntityName, ServerClusterRoles.Backend, sharding, numberOfShards) {

  import ModelOperationServiceActorSharding._

  override def createBehavior(domainId: DomainId,
                              props: Props,
                              shardRegion: ActorRef[ModelOperationServiceActor.Message],
                              entityContext: EntityContext[ModelOperationServiceActor.Message]): Behavior[ModelOperationServiceActor.Message] = {
    val Props(domainPersistenceManager, domainPassivationTimeout) = props

    ModelOperationServiceActor(
      domainId,
      shardRegion,
      entityContext.shard,
      domainPersistenceManager,
      domainPassivationTimeout)
  }

  override protected def createProperties(): Props = {
    val passivationTimeout = Duration.fromNanos(
      config.getDuration("convergence.realtime.domain.passivation-timeout").toNanos)

    Props(DomainPersistenceManagerActor, passivationTimeout)
  }

  override protected def getDomainId(m: ModelOperationServiceActor.Message): DomainId = m.domainId
}

object ModelOperationServiceActorSharding {
  private val EntityName = "ModelOperationServiceActor"

  def apply(config: Config,
            sharding: ClusterSharding,
            numberOfShards: Int): ActorRef[ModelOperationServiceActor.Message] = {
    val domainSharding = new ModelOperationServiceActorSharding(config, sharding, numberOfShards)
    domainSharding.shardRegion
  }

  final case class Props(domainPersistenceManager: DomainPersistenceManager, domainPassivationTimeout: FiniteDuration)
}
