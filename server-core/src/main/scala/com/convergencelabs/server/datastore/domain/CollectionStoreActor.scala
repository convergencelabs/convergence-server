package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.CollectionStoreActor.CreateCollection
import com.convergencelabs.server.datastore.CollectionStoreActor.DeleteCollection
import com.convergencelabs.server.datastore.domain.CollectionStore
import com.convergencelabs.server.domain.model.Collection

import CollectionStoreActor.GetCollection
import CollectionStoreActor.GetCollections
import akka.actor.ActorLogging
import akka.actor.Props

object CollectionStoreActor {
  def props(collectionStore: CollectionStore): Props = Props(new CollectionStoreActor(collectionStore))

  trait CollectionStoreRequest
  case class GetCollections(offset: Option[Int], limit: Option[Int]) extends CollectionStoreRequest
  case class GetCollection(id: String) extends CollectionStoreRequest
  case class DeleteCollection(collectionId: String) extends CollectionStoreRequest
  case class CreateCollection(collection: Collection) extends CollectionStoreRequest
}

class CollectionStoreActor private[datastore] (
  private[this] val collectionStore: CollectionStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetCollections(offset, limit)  => getCollections(offset, limit)
    case GetCollection(collectionId)    => getCollectionConfig(collectionId)
    case CreateCollection(collection)   => createCollection(collection)
    case DeleteCollection(collectionId) => deleteCollection(collectionId)
    case message: Any                   => unhandled(message)
  }

  def getCollections(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(collectionStore.getAllCollections(offset, limit))
  }

  def getCollectionConfig(id: String): Unit = {
    reply(collectionStore.getCollection(id))
  }

  def createCollection(collection: Collection): Unit = {
    reply(collectionStore.createCollection(collection))
  }

  def deleteCollection(collectionId: String): Unit = {
    reply(collectionStore.deleteCollection(collectionId))
  }
}
