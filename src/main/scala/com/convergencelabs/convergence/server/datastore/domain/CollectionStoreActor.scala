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

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.domain.CollectionStore.CollectionSummary
import com.convergencelabs.convergence.server.domain.model.Collection
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}

class CollectionStoreActor private[datastore](private[this] val context: ActorContext[CollectionStoreActor.Message],
                                              private[this] val collectionStore: CollectionStore)
  extends AbstractBehavior[CollectionStoreActor.Message](context) with Logging {

  import CollectionStoreActor._

  override def onMessage(message: Message): Behavior[Message] = {
    message match {
      case msg: GetCollectionsRequest =>
        onGetCollections(msg)
      case msg: GetCollectionSummariesRequest =>
        onGetCollectionSummaries(msg)
      case msg: GetCollectionRequest =>
        onGetCollection(msg)
      case msg: CreateCollectionRequest =>
        onCreateCollection(msg)
      case msg: DeleteCollectionRequest =>
        onDeleteCollection(msg)
      case msg: UpdateCollectionRequest =>
        onUpdateCollection(msg)
    }

    Behaviors.same
  }

  private[this] def onGetCollections(msg: GetCollectionsRequest): Unit = {
    val GetCollectionsRequest(filter, offset, limit, replyTo) = msg
    collectionStore.getAllCollections(filter, offset, limit) match {
      case Success(collections) =>
        replyTo ! GetCollectionsSuccess(collections)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetCollectionSummaries(msg: GetCollectionSummariesRequest): Unit = {
    val GetCollectionSummariesRequest(filter, offset, limit, replyTo) = msg
    collectionStore.getCollectionSummaries(filter, offset, limit) match {
      case Success(collections) =>
        replyTo ! GetCollectionSummariesSuccess(collections)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetCollection(msg: GetCollectionRequest): Unit = {
    val GetCollectionRequest(collectionId, replyTo) = msg
    collectionStore.getCollection(collectionId) match {
      case Success(collection) =>
        replyTo ! GetCollectionSuccess(collection)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onCreateCollection(msg: CreateCollectionRequest): Unit = {
    val CreateCollectionRequest(collection, replyTo) = msg
    collectionStore.createCollection(collection) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onUpdateCollection(msg: UpdateCollectionRequest): Unit = {
    val UpdateCollectionRequest(collectionId, collection, replyTo) = msg
    collectionStore.updateCollection(collectionId, collection) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onDeleteCollection(msg: DeleteCollectionRequest): Unit = {
    val DeleteCollectionRequest(collectionId, replyTo) = msg
    collectionStore.deleteCollection(collectionId) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}

object CollectionStoreActor {
  def apply(collectionStore: CollectionStore): Behavior[Message] = Behaviors.setup { context =>
    new CollectionStoreActor(context, collectionStore)
  }

  trait Message extends CborSerializable with DomainRestMessageBody

  case class GetCollectionsRequest(filter: Option[String],
                                   offset: Option[Int],
                                   limit: Option[Int],
                                   replyTo: ActorRef[GetCollectionsResponse]) extends Message

  sealed trait GetCollectionsResponse extends CborSerializable

  case class GetCollectionsSuccess(collections: PagedData[Collection]) extends GetCollectionsResponse


  case class GetCollectionSummariesRequest(filter: Option[String],
                                           offset: Option[Int],
                                           limit: Option[Int],
                                           replyTo: ActorRef[GetCollectionSummariesResponse]) extends Message

  sealed trait GetCollectionSummariesResponse extends CborSerializable

  case class GetCollectionSummariesSuccess(collections: PagedData[CollectionSummary]) extends GetCollectionSummariesResponse


  case class GetCollectionRequest(id: String, replyTo: ActorRef[GetCollectionResponse]) extends Message

  sealed trait GetCollectionResponse extends CborSerializable

  case class GetCollectionSuccess(collection: Option[Collection]) extends GetCollectionResponse


  case class DeleteCollectionRequest(collectionId: String, replyTo: ActorRef[DeleteCollectionResponse]) extends Message

  sealed trait DeleteCollectionResponse extends CborSerializable

  case class CreateCollectionRequest(collection: Collection, replyTo: ActorRef[CreateCollectionResponse]) extends Message

  sealed trait CreateCollectionResponse extends CborSerializable

  case class UpdateCollectionRequest(collectionId: String, collection: Collection, replyTo: ActorRef[UpdateCollectionResponse]) extends Message

  sealed trait UpdateCollectionResponse extends CborSerializable


  case class RequestFailure(cause: Throwable) extends CborSerializable
    with GetCollectionsResponse
    with GetCollectionSummariesResponse
    with GetCollectionResponse
    with DeleteCollectionResponse
    with CreateCollectionResponse
    with UpdateCollectionResponse

  case class RequestSuccess() extends CborSerializable
    with DeleteCollectionResponse
    with CreateCollectionResponse
    with UpdateCollectionResponse

}
