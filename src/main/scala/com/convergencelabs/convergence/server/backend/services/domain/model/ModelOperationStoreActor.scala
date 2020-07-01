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

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.backend.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.backend.datastore.domain.model.ModelOperationStore
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

class ModelOperationStoreActor private(context: ActorContext[ModelOperationStoreActor.Message],
                                       operationStore: ModelOperationStore)
  extends AbstractBehavior[ModelOperationStoreActor.Message](context) {

  import ModelOperationStoreActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case msg: GetOperationsRequest =>
        handleGetOperations(msg)
    }
    Behaviors.same
  }

  def handleGetOperations(msg: GetOperationsRequest): Unit = {
    val GetOperationsRequest(modelId, first, last, replyTo) = msg
    operationStore.getOperationsInVersionRange(modelId, first, last)
      .map(ops => GetOperationsResponse(Right(ops)))
      .recover {
        case _: EntityNotFoundException =>
          GetOperationsResponse(Left(ModelNotFoundError()))
        case cause =>
          context.log.error("Unexpected error getting model operations", cause)
          GetOperationsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }
}

object ModelOperationStoreActor {
  def apply(operationStore: ModelOperationStore): Behavior[Message] =
    Behaviors.setup(context => new ModelOperationStoreActor(context, operationStore))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GetOperationsRequest], name = "get_ops")
  ))
  sealed trait Message extends CborSerializable

  //
  // GetOperations
  //
  final case class GetOperationsRequest(modelId: String,
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
