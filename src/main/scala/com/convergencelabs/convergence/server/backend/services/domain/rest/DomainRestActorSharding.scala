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

package com.convergencelabs.convergence.server.backend.services.domain.rest

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityContext}
import com.convergencelabs.convergence.server.ConvergenceServerConstants.ServerClusterRoles
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatServiceActor
import com.convergencelabs.convergence.server.backend.services.domain.model.ModelServiceActor
import com.convergencelabs.convergence.server.backend.services.domain.{DomainIdBasedActorSharding, DomainPersistenceManager, DomainPersistenceManagerActor}
import com.convergencelabs.convergence.server.model.DomainId
import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}

final class DomainRestActorSharding private(config: Config,
                                                    sharding: ClusterSharding,
                                                    numberOfShards: Int,
                                                    modelServiceActor: ActorRef[ModelServiceActor.Message],
                                                    chatServiceActor: ActorRef[ChatServiceActor.Message]
                                                   )
  extends DomainIdBasedActorSharding[DomainRestActor.Message, DomainRestActorSharding.Props](DomainRestActorSharding.EntityName, ServerClusterRoles.Backend, sharding, numberOfShards) {

  import DomainRestActorSharding._

  override def getDomainId(msg: DomainRestActor.Message): DomainId = msg.domainId

  override def createBehavior(domainId: DomainId,
                              props: Props,
                              shardRegion: ActorRef[DomainRestActor.Message],
                              entityContext: EntityContext[DomainRestActor.Message]): Behavior[DomainRestActor.Message] = {
    val Props(domainPersistenceManager, domainPassivationTimeout) = props

    DomainRestActor(
      domainId,
      shardRegion,
      entityContext.shard,
      domainPersistenceManager,
      domainPassivationTimeout,
      modelServiceActor,
      chatServiceActor)
  }

  override protected def createProperties(): Props = {
    val receiveTimeout = Duration.fromNanos(
      config.getDuration("convergence.realtime.model.passivation-timeout").toNanos)
    Props(DomainPersistenceManagerActor, receiveTimeout)
  }
}

object DomainRestActorSharding {
  private val EntityName = "DomainRestActor"

  def apply(config: Config,
            sharding: ClusterSharding,
            numberOfShards: Int,
            modelServiceActor: ActorRef[ModelServiceActor.Message],
            chatServiceActor: ActorRef[ChatServiceActor.Message]): ActorRef[DomainRestActor.Message] = {
    val restSharding = new DomainRestActorSharding(config, sharding, numberOfShards, modelServiceActor, chatServiceActor)
    restSharding.shardRegion
  }

  case class Props(domainPersistenceManager: DomainPersistenceManager, receiveTimeout: FiniteDuration)
}