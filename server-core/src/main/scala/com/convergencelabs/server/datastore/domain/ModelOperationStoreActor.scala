package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.datastore.StoreActor

import akka.actor.ActorLogging
import akka.actor.Props

object ModelOperationStoreActor {
  def props(operationStore: ModelOperationStore): Props = Props(new ModelOperationStoreActor(operationStore))

  trait ModelOperationStoreRequest

  case class GetOperations(modelId: String, first: Long, last: Long) extends ModelOperationStoreRequest
}

class ModelOperationStoreActor private[datastore] (
  private[this] val operationStore: ModelOperationStore)
    extends StoreActor with ActorLogging {
  import ModelOperationStoreActor._
  def receive: Receive = {
    case GetOperations(modelId, first, last) => getOperations(modelId, first, last)
    case message: Any => unhandled(message)
  }

  def getOperations(modelId: String, first: Long, last: Long): Unit = {
    reply(operationStore.getOperationsInVersionRange(modelId, first, last))
  }
}
