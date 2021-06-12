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

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.server.backend.datastore.domain._
import com.convergencelabs.convergence.server.backend.services.domain.DomainPersistenceManagerActor.DomainNotFoundException
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.util.actor._
import grizzled.slf4j.Logging

import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}

abstract class BaseDomainShardedActor[M](domainId: DomainId,
                                         context: ActorContext[M],
                                         shardRegion: ActorRef[M],
                                         shard: ActorRef[ClusterSharding.ShardCommand],
                                         domainPersistenceManager: DomainPersistenceManager,
                                         receiveTimeout: FiniteDuration)
  extends SimpleShardedActor[M](context, shardRegion, shard, s"${domainId.namespace}/${domainId.domainId}") with Logging {

  // This is the state that will be set during the initialize method
  protected var persistenceProvider: DomainPersistenceProvider = _

  enableReceiveTimeout()

  def enableReceiveTimeout(): Unit = {
    this.context.setReceiveTimeout(this.receiveTimeout, getReceiveTimeoutMessage())
  }

  def disableReceiveTimeout(): Unit = {
    this.context.cancelReceiveTimeout()
  }


  //
  // Initialization
  //

  override def initialize(msg: M): Try[ShardedActorStatUpPlan] = {
    val domainId = getDomainId(msg)

    domainPersistenceManager.acquirePersistenceProvider(context.self, context.system, domainId)
      .map { provider =>
        this.persistenceProvider = provider
      }
      .flatMap(_ => initializeState(msg))
      .map(_ => StartUpRequired)
      .recoverWith {
        // This is a special case, we know the domain was not found. In theory this
        // should have been a handshake message, and we want to respond.
        case _: DomainNotFoundException =>
          handleDomainNotFound(msg)
          Success(StartUpNotRequired)
      }
  }

  override def passivate(): Behavior[M] = {
    Option(this.persistenceProvider).foreach(_ =>
      domainPersistenceManager.releasePersistenceProvider(context.self, context.system, this.domainId)
    )
    super.passivate()
  }

  protected def handleDomainNotFound(msg: M): Unit = ()

  protected def initializeState(msg: M): Try[Unit] = Success(())

  protected def getDomainId(msg: M): DomainId

  protected def getReceiveTimeoutMessage(): M


}



