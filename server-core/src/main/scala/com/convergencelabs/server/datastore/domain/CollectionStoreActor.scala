package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.CollectionStore
import com.convergencelabs.server.domain.DomainFqn

import CollectionStoreActor.CollectionInfo
import akka.actor.ActorLogging
import akka.actor.Props

object CollectionStoreActor {
  def props(collectionStore: CollectionStore): Props = Props(new CollectionStoreActor(collectionStore))

  trait CollectionStoreRequest
  case class GetCollections(offset: Option[Int], limit: Option[Int]) extends CollectionStoreRequest
  case class GetCollection(id: String) extends CollectionStoreRequest
  
  case class CollectionInfo(id: String, name: String)
}

class CollectionStoreActor private[datastore] (
  private[this] val collectionStore: CollectionStore)
    extends StoreActor with ActorLogging {

  import CollectionStoreActor._
  
  def receive: Receive = {
    case GetCollections(offset, limit) => getCollections(offset, limit)
    case GetCollection(collectionId) => getCollectionConfig(collectionId)
    case message: Any => unhandled(message)
  }

  def getCollections(offset: Option[Int], limit: Option[Int]): Unit = {
    mapAndReply(collectionStore.getAllCollections(offset, limit)) { collections =>
      collections.map { c => CollectionInfo(c.id, c.name) }
    }
  }
  
  def getCollectionConfig(id: String): Unit = {
    reply(collectionStore.getCollection(id))
  }
}
