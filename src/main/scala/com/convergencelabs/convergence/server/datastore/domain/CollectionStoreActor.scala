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
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.datastore.domain.CollectionStore.CollectionSummary
import com.convergencelabs.convergence.server.domain.model.Collection

class CollectionStoreActor private[datastore] (
  private[this] val collectionStore: CollectionStore)
  extends StoreActor with ActorLogging {

  import CollectionStoreActor._

  def receive: Receive = {
    case GetCollectionsRequest(filter, offset, limit) =>
      onGetCollections(filter, offset, limit)
    case GetCollectionSummariesRequest(filter, offset, limit) =>
      onGetCollectionSummaries(filter, offset, limit)
    case GetCollectionRequest(collectionId) =>
      onGetCollectionConfig(collectionId)
    case CreateCollection(collection) =>
      onCreateCollection(collection)
    case DeleteCollection(collectionId) =>
      onDeleteCollection(collectionId)
    case UpdateCollection(collectionId, collection) =>
      onUpdateCollection(collectionId, collection)
    case message: Any =>
      unhandled(message)
  }

  private[this] def onGetCollections(filter: Option[String], offset: Option[Int], limit: Option[Int]): Unit = {
    reply(collectionStore.getAllCollections(filter, offset, limit).map(GetCollectionsResponse))
  }

  private[this] def onGetCollectionSummaries(filter: Option[String], offset: Option[Int], limit: Option[Int]): Unit = {
    reply(collectionStore.getCollectionSummaries(filter, offset, limit).map(GetCollectionSummariesResponse))
  }

  private[this] def onGetCollectionConfig(id: String): Unit = {
    reply(collectionStore.getCollection(id))
  }

  private[this] def onCreateCollection(collection: Collection): Unit = {
    reply(collectionStore.createCollection(collection))
  }

  private[this] def onUpdateCollection(collectionId: String, collection: Collection): Unit = {
    reply(collectionStore.updateCollection(collectionId, collection))
  }

  private[this] def onDeleteCollection(collectionId: String): Unit = {
    reply(collectionStore.deleteCollection(collectionId))
  }
}

object CollectionStoreActor {
  def props(collectionStore: CollectionStore): Props = Props(new CollectionStoreActor(collectionStore))

  trait CollectionStoreRequest extends CborSerializable
  case class GetCollectionsRequest(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends CollectionStoreRequest
  case class GetCollectionsResponse(collections: PagedData[Collection]) extends CborSerializable

  case class GetCollectionSummariesRequest(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends CollectionStoreRequest
  case class GetCollectionSummariesResponse(collections: PagedData[CollectionSummary]) extends CborSerializable

  case class GetCollectionRequest(id: String) extends CollectionStoreRequest
  case class GetCollectionResponse(collection: Option[Collection]) extends CborSerializable

  case class DeleteCollection(collectionId: String) extends CollectionStoreRequest
  case class CreateCollection(collection: Collection) extends CollectionStoreRequest
  case class UpdateCollection(collectionId: String, collection: Collection) extends CollectionStoreRequest
}
