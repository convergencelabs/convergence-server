package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.CollectionStore
import com.convergencelabs.server.domain.DomainFqn
import CollectionStoreActor.CollectionInfo
import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.domain.model.ModelFqn

object ModelStoreActor {
  def props(modelStore: ModelStore): Props = Props(new ModelStoreActor(modelStore))

  trait ModelStoreRequest
  
  case class GetModels(offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
  case class GetModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
  case class GetModel(modelFqn: ModelFqn) extends ModelStoreRequest
  
  case class CollectionInfo(id: String, name: String)
}

class ModelStoreActor private[datastore] (
  private[this] val modelStore: ModelStore)
    extends StoreActor with ActorLogging {

  import ModelStoreActor._
  
  def receive: Receive = {
    case GetModels(offset, limit) => getModels(offset, limit)
    case GetModelsInCollection(collectionId, offset, limit) => getModelsInCollection(collectionId, offset, limit)
    case GetModel(modelFqn) => getModel(modelFqn)
    case message: Any => unhandled(message)
  }

  def getModels(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(modelStore.getAllModelMetaData(offset, limit))
  }
  
  def getModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]): Unit = {
    reply(modelStore.getAllModelMetaDataInCollection(collectionId, offset, limit))
  }
  
  def getModel(modelFqn: ModelFqn): Unit = {
    reply(modelStore.getModel(modelFqn))
  }
}
