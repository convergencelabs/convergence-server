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

package com.convergencelabs.convergence.server.datastore.domain

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.domain.model.ModelOperation
import grizzled.slf4j.Logging

class ModelOperationStoreActor private(context: ActorContext[ModelOperationStoreActor.Message],
                                       operationStore: ModelOperationStore)
  extends AbstractBehavior[ModelOperationStoreActor.Message](context) with Logging {

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
        case _ =>
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

  sealed trait Message extends CborSerializable

  case class GetOperationsRequest(modelId: String, first: Long, last: Long, replyTo: ActorRef[GetOperationsResponse]) extends Message

  sealed trait GetOperationsError

  case class GetOperationsResponse(operations: Either[GetOperationsError, List[ModelOperation]]) extends CborSerializable

  sealed trait RequestError

  case class ModelNotFoundError() extends RequestError
    with GetOperationsError

  case class UnknownError() extends RequestError
    with GetOperationsError

}
