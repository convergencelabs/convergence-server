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

package com.convergencelabs.convergence.server.domain

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityContext}
import com.convergencelabs.convergence.server.actor.ActorSharding
import com.convergencelabs.convergence.server.datastore.domain.{DomainPersistenceManager, DomainPersistenceManagerActor}
import com.convergencelabs.convergence.server.db.provision.DomainLifecycleTopic
import com.convergencelabs.convergence.server.{ProtocolConfigUtil, ProtocolConfiguration, ServerClusterRoles}

import scala.concurrent.duration.{Duration, FiniteDuration}

 class DomainActorSharding private(system: ActorSystem[_],
                                  sharding: ClusterSharding,
                                  numberOfShards: Int,
                                  domainLifecycleTopic: () => ActorRef[DomainLifecycleTopic.TopicMessage])
  extends ActorSharding[DomainActor.Message, Props](DomainActorSharding.EntityName, ServerClusterRoles.Backend, system, sharding, numberOfShards) {
  override def extractEntityId(msg: DomainActor.Message): String = s"${msg.domainId.namespace}::${msg.domainId.domainId}"

  override def createBehavior(props: Props,
                              system: ActorSystem[_],
                              shardRegion: ActorRef[DomainActor.Message],
                              entityContext: EntityContext[DomainActor.Message]): Behavior[DomainActor.Message] = {
    val Props(protocolConfig, domainPersistenceManager, domainPassivationTimeout, domainLifecycleTopic) = props
    DomainActor(shardRegion,
      entityContext.shard,
      protocolConfig,
      domainPersistenceManager,
      domainPassivationTimeout,
      domainLifecycleTopic)
  }

  override protected def createProperties(): Props = {
    val protocolConfig = ProtocolConfigUtil.loadConfig(system.settings.config)
    val domainPassivationTimeout = Duration.fromNanos(
      system.settings.config.getDuration("convergence.realtime.domain.passivation-timeout").toNanos)

    Props(protocolConfig: ProtocolConfiguration,
      DomainPersistenceManagerActor,
      domainPassivationTimeout: FiniteDuration,
      domainLifecycleTopic())
  }
}


case class Props(protocolConfig: ProtocolConfiguration,
                 domainPersistenceManager: DomainPersistenceManager,
                 domainPassivationTimeout: FiniteDuration,
                 domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])


object DomainActorSharding {
  private val EntityName = "DomainActor"

  def apply(system: ActorSystem[_],
            sharding: ClusterSharding,
            numberOfShards: Int,
            domainLifecycleTopic: () => ActorRef[DomainLifecycleTopic.TopicMessage]): ActorRef[DomainActor.Message] = {
    val domainSharding = new DomainActorSharding(system, sharding, numberOfShards, domainLifecycleTopic)
    domainSharding.shardRegion
  }
}