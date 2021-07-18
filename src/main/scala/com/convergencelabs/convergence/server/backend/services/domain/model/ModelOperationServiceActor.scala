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

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.server.backend.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.backend.services.domain.{BaseDomainShardedActor, DomainPersistenceManager}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

import scala.concurrent.duration.FiniteDuration

final class ModelOperationServiceActor private(domainId: DomainId,
                                               context: ActorContext[ModelOperationServiceActor.Message],
                                               shardRegion: ActorRef[ModelOperationServiceActor.Message],
                                               shard: ActorRef[ClusterSharding.ShardCommand],
                                               domainPersistenceManager: DomainPersistenceManager,
                                               receiveTimeout: FiniteDuration)
  extends BaseDomainShardedActor[ModelOperationServiceActor.Message](domainId, context, shardRegion, shard, domainPersistenceManager, receiveTimeout) {

  import ModelOperationServiceActor._

  override def receiveInitialized(msg: Message): Behavior[Message] = {
    msg match {
      case msg: GetOperationsRequest =>
        onGetOperations(msg)
      case ReceiveTimeout(_) =>
        this.passivate()
    }
  }

  def onGetOperations(msg: GetOperationsRequest): Behavior[Message] = {
    val GetOperationsRequest(this.domainId, modelId, first, last, replyTo) = msg
    this.persistenceProvider.modelOperationStore.getOperationsInVersionRange(modelId, first, last)
      .map(ops => Right(ops))
      .recover {
        case _: EntityNotFoundException =>
          Left(ModelNotFoundError())
        case cause =>
          context.log.error("Unexpected error getting model operations", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetOperationsResponse(_))

    Behaviors.same
  }



  override protected def getDomainId(msg: Message): DomainId = msg.domainId

  override protected def getReceiveTimeoutMessage(): Message = ReceiveTimeout(this.domainId)
}

object ModelOperationServiceActor {

  def apply(domainId: DomainId,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration): Behavior[Message] = Behaviors.setup { context =>
    new ModelOperationServiceActor(
      domainId,
      context,
      shardRegion,
      shard,
      domainPersistenceManager,
      receiveTimeout)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GetOperationsRequest], name = "get_ops")
  ))
  sealed trait Message extends CborSerializable {
    val domainId: DomainId
  }

  private final case class ReceiveTimeout(domainId: DomainId) extends Message

  //
  // GetOperations
  //
  final case class GetOperationsRequest(domainId: DomainId,
                                        modelId: String,
                                        first: Long,
                                        last: Long,
                                        replyTo: ActorRef[GetOperationsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "model_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetOperationsError

  final case class GetOperationsResponse(operations: Either[GetOperationsError, List[ModelOperation]]) extends CborSerializable


  //
  // Common Errors
  //
  final case class ModelNotFoundError() extends AnyRef
    with GetOperationsError


  final case class UnknownError() extends AnyRef
    with GetOperationsError

}
