/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

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
  case class GetCollections(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends CollectionStoreRequest
  case class GetCollectionSummaries(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends CollectionStoreRequest
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
    case GetCollections(filter, offset, limit) => getCollections(filter, offset, limit)
    case GetCollectionSummaries(filter, offset, limit) => getCollectionSummaries(filter, offset, limit)
    case GetCollection(collectionId) => getCollectionConfig(collectionId)
    case CreateCollection(collection) => createCollection(collection)
    case DeleteCollection(collectionId) => deleteCollection(collectionId)
    case UpdateCollection(collectionId, collection) => updateCollection(collectionId, collection)
    case message: Any => unhandled(message)
  }

  def getCollections(filter: Option[String], offset: Option[Int], limit: Option[Int]): Unit = {
    reply(collectionStore.getAllCollections(filter, offset, limit))
  }

  def getCollectionSummaries(filter: Option[String], offset: Option[Int], limit: Option[Int]): Unit = {
    reply(collectionStore.getCollectionSummaries(filter, offset, limit))
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
