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

package com.convergencelabs.convergence.server.domain.model

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityContext}
import com.convergencelabs.convergence.server.ServerClusterRoles
import com.convergencelabs.convergence.server.actor.ActorSharding
import com.convergencelabs.convergence.server.datastore.domain.{DomainPersistenceManager, DomainPersistenceManagerActor}

import scala.concurrent.duration.{Duration, FiniteDuration}

object RealtimeModelSharding {
  private val EntityName = "RealtimeModel"

  def apply(system: ActorSystem[_], sharding: ClusterSharding, numberOfShards: Int): ActorRef[RealtimeModelActor.Message] = {
    val modelSharding = new RealtimeModelSharding(system, sharding, numberOfShards)
    modelSharding.shardRegion
  }
}

private class RealtimeModelSharding(system: ActorSystem[_], sharding: ClusterSharding, numberOfShards: Int)
  extends ActorSharding[RealtimeModelActor.Message, Props](RealtimeModelSharding.EntityName, ServerClusterRoles.Backend, system, sharding, numberOfShards) {

  override def extractEntityId(message: RealtimeModelActor.Message): String =
    s"${message.domainId.namespace}::${message.domainId.domainId}::${message.modelId}"

  override def createBehavior(props: Props, system: ActorSystem[_], shardRegion: ActorRef[RealtimeModelActor.Message], entityContext: EntityContext[RealtimeModelActor.Message]): Behavior[RealtimeModelActor.Message] = {
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
      system.settings.config.getDuration("convergence.realtime.model.client-data-timeout").toNanos)
    val receiveTimeout = Duration.fromNanos(
      system.settings.config.getDuration("convergence.realtime.model.passivation-timeout").toNanos)
    val resyncTimeout = Duration.fromNanos(
      system.settings.config.getDuration("convergence.realtime.model.resynchronization-timeout").toNanos)

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

