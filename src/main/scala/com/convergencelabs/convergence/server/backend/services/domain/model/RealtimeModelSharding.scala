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
import com.convergencelabs.convergence.server.actor.ActorSharding
import com.convergencelabs.convergence.server.backend.datastore.domain.DomainPersistenceManager
import com.convergencelabs.convergence.server.backend.services.domain.DomainPersistenceManager
import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}

object RealtimeModelSharding {
  private val EntityName = "RealtimeModel"

  def apply(config: Config, sharding: ClusterSharding, numberOfShards: Int): ActorRef[RealtimeModelActor.Message] = {
    val modelSharding = new RealtimeModelSharding(config, sharding, numberOfShards)
    modelSharding.shardRegion
  }
}

private class RealtimeModelSharding(config: Config, sharding: ClusterSharding, numberOfShards: Int)
  extends ActorSharding[RealtimeModelActor.Message, Props](RealtimeModelSharding.EntityName, ServerClusterRoles.Backend, sharding, numberOfShards) {

  override def extractEntityId(message: RealtimeModelActor.Message): String =
    s"${message.domainId.namespace}::${message.domainId.domainId}::${message.modelId}"

  override def createBehavior(props: Props, shardRegion: ActorRef[RealtimeModelActor.Message], entityContext: EntityContext[RealtimeModelActor.Message]): Behavior[RealtimeModelActor.Message] = {
    val Props(modelPermissionResolver, modelCreator, persistenceManager, clientDataResponseTimeout, receiveTimeout, resyncTimeout) = props
    RealtimeModelActor(
      shardRegion,
      entityContext.shard,
      modelPermissionResolver,
      modelCreator,
      persistenceManager,
      clientDataResponseTimeout,
      receiveTimeout,
      resyncTimeout)
  }

  override protected def createProperties(): Props = {
    val clientDataResponseTimeout = Duration.fromNanos(
      config.getDuration("convergence.realtime.model.client-data-timeout").toNanos)
    val receiveTimeout = Duration.fromNanos(
      config.getDuration("convergence.realtime.model.passivation-timeout").toNanos)
    val resyncTimeout = Duration.fromNanos(
      config.getDuration("convergence.realtime.model.resynchronization-timeout").toNanos)

    Props(new ModelPermissionResolver(),
      new ModelCreator(),
      DomainPersistenceManagerActor,
      clientDataResponseTimeout,
      receiveTimeout,
      resyncTimeout)
  }
}

private case class Props(modelPermissionResolver: ModelPermissionResolver,
                         modelCreator: ModelCreator,
                         persistenceManager: DomainPersistenceManager,
                         clientDataResponseTimeout: FiniteDuration,
                         receiveTimeout: FiniteDuration,
                         resyncTimeout: FiniteDuration)

