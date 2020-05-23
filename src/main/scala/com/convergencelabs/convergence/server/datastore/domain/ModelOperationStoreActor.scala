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

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.domain.model.ModelOperation

class ModelOperationStoreActor private[datastore] (
  private[this] val operationStore: ModelOperationStore)
    extends StoreActor with ActorLogging {
  import ModelOperationStoreActor._
  def receive: Receive = {
    case GetOperationsRequest(modelId, first, last) =>
      handleGetOperations(modelId, first, last)
    case message: Any =>
      unhandled(message)
  }

  def handleGetOperations(modelId: String, first: Long, last: Long): Unit = {
    reply(operationStore.getOperationsInVersionRange(modelId, first, last).map(GetOperationsResponse))
  }
}

object ModelOperationStoreActor {
  val RelativePath = "ModelOperationStoreActor"

  def props(operationStore: ModelOperationStore): Props = Props(new ModelOperationStoreActor(operationStore))

  trait ModelOperationStoreRequest extends CborSerializable

  case class GetOperationsRequest(modelId: String, first: Long, last: Long) extends ModelOperationStoreRequest

  case class GetOperationsResponse(operations: List[ModelOperation]) extends CborSerializable
}
