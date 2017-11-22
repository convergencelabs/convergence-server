package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.domain.model.Collection

import CollectionStoreActor.GetCollection
import CollectionStoreActor.GetCollections
import akka.actor.ActorLogging
import akka.actor.Props

object CollectionStoreActor {
  def props(collectionStore: CollectionStore): Props = Props(new CollectionStoreActor(collectionStore))

  trait CollectionStoreRequest
  case class GetCollections(offset: Option[Int], limit: Option[Int]) extends CollectionStoreRequest
  case class GetCollectionSummaries(offset: Option[Int], limit: Option[Int]) extends CollectionStoreRequest
  case class GetCollection(id: String) extends CollectionStoreRequest
  case class DeleteCollection(collectionId: String) extends CollectionStoreRequest
  case class CreateCollection(collection: Collection) extends CollectionStoreRequest
  case class UpdateCollection(collectionId: String, collection: Collection) extends CollectionStoreRequest
}

class CollectionStoreActor private[datastore] (
  private[this] val collectionStore: CollectionStore)
    extends StoreActor with ActorLogging {

  import CollectionStoreActor._
  
  def receive: Receive = {
    case GetCollections(offset, limit)  => getCollections(offset, limit)
    case GetCollectionSummaries(offset, limit)  => getCollectionSummaries(offset, limit)
    case GetCollection(collectionId)    => getCollectionConfig(collectionId)
    case CreateCollection(collection)   => createCollection(collection)
    case DeleteCollection(collectionId) => deleteCollection(collectionId)
    case UpdateCollection(collectionId, collection) => updateCollection(collectionId, collection)
    case message: Any                   => unhandled(message)
  }

  def getCollections(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(collectionStore.getAllCollections(offset, limit))
  }
  
  def getCollectionSummaries(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(collectionStore.getCollectionSummaries(offset, limit))
  }

  def getCollectionConfig(id: String): Unit = {
    reply(collectionStore.getCollection(id))
  }

  def createCollection(collection: Collection): Unit = {
    reply(collectionStore.createCollection(collection))
  }
  
  def updateCollection(collectionId: String, collection: Collection): Unit = {
    reply(collectionStore.updateCollection(collectionId, collection))
  }

  def deleteCollection(collectionId: String): Unit = {
    reply(collectionStore.deleteCollection(collectionId))
  }
}
