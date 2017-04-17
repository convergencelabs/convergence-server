package com.convergencelabs.server.datastore

import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.datastore.domain.ModelOperationStore
import com.convergencelabs.server.datastore.ModelOperationStoreActor.GetOperations

object ModelOperationStoreActor {
  def props(operationStore: ModelOperationStore): Props = Props(new ModelOperationStoreActor(operationStore))

  trait ModelOperationStoreRequest

  case class GetOperations(modelId: String, first: Long, last: Long) extends ModelOperationStoreRequest
}

class ModelOperationStoreActor private[datastore] (
  private[this] val operationStore: ModelOperationStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetOperations(modelId, first, last) => getOperations(modelId, first, last)
    case message: Any => unhandled(message)
  }

  def getOperations(modelId: String, first: Long, last: Long): Unit = {
    reply(operationStore.getOperationsInVersionRange(modelId, first, last))
  }
}
