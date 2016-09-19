package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.model.ModelFqn

import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.datastore.domain.ModelOperationStore
import com.convergencelabs.server.datastore.ModelOperationStoreActor.GetOperations

object ModelOperationStoreActor {
  def props(operationStore: ModelOperationStore): Props = Props(new ModelOperationStoreActor(operationStore))

  trait ModelOperationStoreRequest

  case class GetOperations(fqn: ModelFqn, version: Long, limit: Int) extends ModelOperationStoreRequest
}

class ModelOperationStoreActor private[datastore] (
  private[this] val operationStore: ModelOperationStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetOperations(fqn, version, limit) => getOperations(fqn, version, limit)
    case message: Any => unhandled(message)
  }

  def getOperations(fqn: ModelFqn, version: Long, limit: Int): Unit = {
    reply(operationStore.getOperationsAfterVersion(fqn, version, limit))
  }
}
