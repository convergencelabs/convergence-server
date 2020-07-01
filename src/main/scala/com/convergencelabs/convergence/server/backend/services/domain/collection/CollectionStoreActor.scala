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

package com.convergencelabs.convergence.server.backend.services.domain.collection

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.{Ok, PagedData}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.backend.datastore.domain.collection.CollectionStore
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.model.domain.collection.{Collection, CollectionSummary}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/**
 * The [[CollectionStoreActor]] provides services to create, remove, and update
 * model collections.
 *
 * @param context         The ActorContext for the behavior.
 * @param collectionStore The [[CollectionStore]] to user for persistence of
 *                        collections.
 */
class CollectionStoreActor private(context: ActorContext[CollectionStoreActor.Message],
                                   collectionStore: CollectionStore)
  extends AbstractBehavior[CollectionStoreActor.Message](context) {

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
    collectionStore
      .getAllCollections(filter, offset, limit)
      .map(c => GetCollectionsResponse(Right(c)))
      .recover {
        case cause =>
          context.log.error("Unexpected error getting collections", cause)
          GetCollectionsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetCollectionSummaries(msg: GetCollectionSummariesRequest): Unit = {
    val GetCollectionSummariesRequest(filter, offset, limit, replyTo) = msg
    collectionStore
      .getCollectionSummaries(filter, offset, limit)
      .map(c => GetCollectionSummariesResponse(Right(c)))
      .recover {
        case cause =>
          context.log.error("Unexpected error getting collection summaries", cause)
          GetCollectionSummariesResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetCollection(msg: GetCollectionRequest): Unit = {
    val GetCollectionRequest(collectionId, replyTo) = msg
    collectionStore
      .getCollection(collectionId)
      .map(_.map(c => GetCollectionResponse(Right(c))).getOrElse(GetCollectionResponse(Left(CollectionNotFoundError()))))
      .recover {
        case cause =>
          context.log.error("Unexpected error getting collection", cause)
          GetCollectionResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onCreateCollection(msg: CreateCollectionRequest): Unit = {
    val CreateCollectionRequest(collection, replyTo) = msg
    collectionStore
      .createCollection(collection)
      .map(_ => CreateCollectionResponse(Right(Ok())))
      .recover {
        case DuplicateValueException(field, _, _) =>
          CreateCollectionResponse(Left(CollectionExists(field)))
        case cause =>
          context.log.error("Unexpected error creating collection", cause)
          CreateCollectionResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onUpdateCollection(msg: UpdateCollectionRequest): Unit = {
    val UpdateCollectionRequest(collectionId, collection, replyTo) = msg
    collectionStore
      .updateCollection(collectionId, collection)
      .map(_ => UpdateCollectionResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          UpdateCollectionResponse(Left(CollectionNotFoundError()))
        case cause =>
          context.log.error("Unexpected error updating collection", cause)
          UpdateCollectionResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onDeleteCollection(msg: DeleteCollectionRequest): Unit = {
    val DeleteCollectionRequest(collectionId, replyTo) = msg
    collectionStore
      .deleteCollection(collectionId)
      .map(_ => DeleteCollectionResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          DeleteCollectionResponse(Left(CollectionNotFoundError()))
      }
      .foreach(replyTo ! _)
  }
}

object CollectionStoreActor {
  def apply(collectionStore: CollectionStore): Behavior[Message] =
    Behaviors.setup(new CollectionStoreActor(_, collectionStore))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CreateCollectionRequest], name = "create_collections"),
    new JsonSubTypes.Type(value = classOf[DeleteCollectionRequest], name = "delete_collections"),
    new JsonSubTypes.Type(value = classOf[GetCollectionRequest], name = "get_collection"),
    new JsonSubTypes.Type(value = classOf[GetCollectionsRequest], name = "get_collections"),
    new JsonSubTypes.Type(value = classOf[GetCollectionSummariesRequest], name = "get_collection_summaries"),
    new JsonSubTypes.Type(value = classOf[UpdateCollectionRequest], name = "update_collection")
  ))
  sealed trait Message extends CborSerializable

  //
  // GetCollections
  //
  final case class GetCollectionsRequest(filter: Option[String],
                                         @JsonDeserialize(contentAs = classOf[java.lang.Long])
                                         offset: QueryOffset,
                                         @JsonDeserialize(contentAs = classOf[java.lang.Long])
                                         limit: QueryLimit,
                                         replyTo: ActorRef[GetCollectionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetCollectionsError

  final case class GetCollectionsResponse(collections: Either[GetCollectionsError, PagedData[Collection]]) extends CborSerializable


  //
  // GetCollectionSummaries
  //
  final case class GetCollectionSummariesRequest(filter: Option[String],
                                                 @JsonDeserialize(contentAs = classOf[java.lang.Long])
                                                 offset: QueryOffset,
                                                 @JsonDeserialize(contentAs = classOf[java.lang.Long])
                                                 limit: QueryLimit,
                                                 replyTo: ActorRef[GetCollectionSummariesResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetCollectionSummariesError

  final case class GetCollectionSummariesResponse(collections: Either[GetCollectionSummariesError, PagedData[CollectionSummary]]) extends CborSerializable


  //
  // GetCollection
  //
  final case class GetCollectionRequest(id: String, replyTo: ActorRef[GetCollectionResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetCollectionError

  final case class GetCollectionResponse(collection: Either[GetCollectionError, Collection]) extends CborSerializable


  //
  // DeleteConnection
  //
  final case class DeleteCollectionRequest(collectionId: String, replyTo: ActorRef[DeleteCollectionResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait DeleteCollectionError

  final case class DeleteCollectionResponse(response: Either[DeleteCollectionError, Ok]) extends CborSerializable

  //
  // CreateCollection
  //
  final case class CreateCollectionRequest(collection: Collection, replyTo: ActorRef[CreateCollectionResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionExists], name = "collection_exists"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait CreateCollectionError

  final case class CollectionExists(field: String) extends CreateCollectionError

  final case class CreateCollectionResponse(response: Either[CreateCollectionError, Ok]) extends CborSerializable

  //
  // UpdateCollection
  //
  final case class UpdateCollectionRequest(collectionId: String, collection: Collection, replyTo: ActorRef[UpdateCollectionResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait UpdateCollectionError

  final case class UpdateCollectionResponse(response: Either[UpdateCollectionError, Ok]) extends CborSerializable


  //
  // Common Errors
  //

  final case class CollectionNotFoundError() extends AnyRef
    with GetCollectionError
    with DeleteCollectionError
    with UpdateCollectionError

  final case class UnknownError() extends AnyRef
    with GetCollectionsError
    with GetCollectionSummariesError
    with GetCollectionError
    with DeleteCollectionError
    with CreateCollectionError
    with UpdateCollectionError

}
