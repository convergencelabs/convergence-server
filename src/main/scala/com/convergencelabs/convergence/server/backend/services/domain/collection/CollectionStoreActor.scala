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
import com.convergencelabs.convergence.server.backend.datastore.domain.collection.{CollectionPermissionsStore, CollectionStore}
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.model.domain.collection.{Collection, CollectionPermissions, CollectionSummary, CollectionWorldAndUserPermissions}
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
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
private final class CollectionStoreActor(context: ActorContext[CollectionStoreActor.Message],
                                         collectionStore: CollectionStore,
                                         collectionPermissionsStore: CollectionPermissionsStore)
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
      case msg: GetCollectionPermissionsRequest =>
        onGetCollectionPermissions(msg)
      case msg: GetCollectionWorldPermissionsRequest =>
        onGetCollectionWorldPermissions(msg)
      case msg: SetCollectionWorldPermissionsRequest =>
        onSetCollectionWorldPermissions(msg)
      case msg: GetCollectionUserPermissionsRequest =>
        onGetCollectionUserPermissions(msg)
      case msg: GetCollectionPermissionsForUserRequest =>
        onGetCollectionPermissionsForUser(msg)
      case msg: SetCollectionPermissionsForUserRequest =>
        onSetCollectionPermissionsForUser(msg)
      case msg: RemoveCollectionPermissionsForUserRequest =>
        onRemoveCollectionPermissionsForUser(msg)
    }

    Behaviors.same
  }

  private[this] def onGetCollections(msg: GetCollectionsRequest): Unit = {
    val GetCollectionsRequest(filter, offset, limit, replyTo) = msg
    collectionStore
      .getAllCollections(filter, offset, limit)
      .map(c => Right(c))
      .recover {
        case cause =>
          context.log.error("Unexpected error getting collections", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetCollectionsResponse(_))
  }

  private[this] def onGetCollectionSummaries(msg: GetCollectionSummariesRequest): Unit = {
    val GetCollectionSummariesRequest(filter, offset, limit, replyTo) = msg
    collectionStore
      .getCollectionSummaries(filter, offset, limit)
      .map(c => Right(c))
      .recover {
        case cause =>
          context.log.error("Unexpected error getting collection summaries", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetCollectionSummariesResponse(_))
  }

  private[this] def onGetCollection(msg: GetCollectionRequest): Unit = {
    val GetCollectionRequest(collectionId, replyTo) = msg
    collectionStore
      .getCollection(collectionId)
      .map(_.map(c => Right(c)).getOrElse(Left(CollectionNotFoundError())))
      .recover {
        case cause =>
          context.log.error("Unexpected error getting collection", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetCollectionResponse(_))
  }

  private[this] def onCreateCollection(msg: CreateCollectionRequest): Unit = {
    val CreateCollectionRequest(collection, replyTo) = msg
    collectionStore
      .createCollection(collection)
      .map(_ => Right(Ok()))
      .recover {
        case DuplicateValueException(field, _, _) =>
          Left(CollectionExists(field))
        case cause =>
          context.log.error("Unexpected error creating collection", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! CreateCollectionResponse(_))
  }

  private[this] def onUpdateCollection(msg: UpdateCollectionRequest): Unit = {
    val UpdateCollectionRequest(collectionId, collection, replyTo) = msg
    collectionStore
      .updateCollection(collectionId, collection)
      .map(_ => Right(Ok()))
      .recover {
        case _: EntityNotFoundException =>
          Left(CollectionNotFoundError())
        case cause =>
          context.log.error("Unexpected error updating collection", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! UpdateCollectionResponse(_))
  }

  private[this] def onDeleteCollection(msg: DeleteCollectionRequest): Unit = {
    val DeleteCollectionRequest(collectionId, replyTo) = msg
    collectionStore
      .deleteCollection(collectionId)
      .map(_ => Right(Ok()))
      .recover {
        case _: EntityNotFoundException =>
          Left(CollectionNotFoundError())
        case _ =>
          Left(UnknownError())
      }
      .foreach(replyTo ! DeleteCollectionResponse(_))
  }

  //
  // Permissions
  //

  def onGetCollectionPermissions(msg: GetCollectionPermissionsRequest): Unit = {
    val GetCollectionPermissionsRequest(collectionId, replyTo) = msg
    (for {
      world <- collectionPermissionsStore.getCollectionWorldPermissions(collectionId)
      user <- collectionPermissionsStore.getAllCollectionUserPermissions(collectionId)
    } yield {
      val response = CollectionWorldAndUserPermissions(world, user)
      Right(response)
    })
      .recover {
        case _: EntityNotFoundException =>
          Left(CollectionNotFoundError())
        case _ =>
          Left(UnknownError())
      }
      .foreach(replyTo ! GetCollectionPermissionsResponse(_))
  }

  def onGetCollectionWorldPermissions(msg: GetCollectionWorldPermissionsRequest): Unit = {
    val GetCollectionWorldPermissionsRequest(collectionId, replyTo) = msg
    collectionPermissionsStore
      .getCollectionWorldPermissions(collectionId)
      .map(world => Right(world))
      .recover {
        case _: EntityNotFoundException =>
          Left(CollectionNotFoundError())
        case _ =>
          Left(UnknownError())
      }
      .foreach(replyTo ! GetCollectionWorldPermissionsResponse(_))
  }

  def onSetCollectionWorldPermissions(msg: SetCollectionWorldPermissionsRequest): Unit = {
    val SetCollectionWorldPermissionsRequest(collectionId, permissions, replyTo) = msg
    collectionPermissionsStore
      .setCollectionWorldPermissions(collectionId, permissions)
      .map(world => Right(Ok()))
      .recover {
        case _: EntityNotFoundException =>
          Left(CollectionNotFoundError())
        case _ =>
          Left(UnknownError())
      }
      .foreach(replyTo ! SetCollectionWorldPermissionsResponse(_))
  }

  def onGetCollectionUserPermissions(msg: GetCollectionUserPermissionsRequest): Unit = {
    val GetCollectionUserPermissionsRequest(collectionId, replyTo) = msg
    collectionPermissionsStore
      .getAllCollectionUserPermissions(collectionId)
      .map(userPermissions => Right(userPermissions))
      .recover {
        case _: EntityNotFoundException =>
          Left(CollectionNotFoundError())
        case _ =>
          Left(UnknownError())
      }
      .foreach(replyTo ! GetCollectionUserPermissionsResponse(_))
  }

  def onGetCollectionPermissionsForUser(msg: GetCollectionPermissionsForUserRequest): Unit = {
    val GetCollectionPermissionsForUserRequest(collectionId, userId, replyTo) = msg
    collectionPermissionsStore
      .getCollectionPermissionsForUser(collectionId, userId)
      .map(userPermissions => Right(userPermissions.getOrElse(CollectionPermissions.None)))
      .recover {
        case _: EntityNotFoundException =>
          Left(CollectionNotFoundError())
        case _ =>
          Left(UnknownError())
      }
      .foreach(replyTo ! GetCollectionPermissionsForUserResponse(_))
  }

  def onSetCollectionPermissionsForUser(msg: SetCollectionPermissionsForUserRequest): Unit = {
    val SetCollectionPermissionsForUserRequest(collectionId, userId, permissions, replyTo) = msg
    collectionPermissionsStore
      .updateCollectionUserPermissions(collectionId, userId, permissions)
      .map(userPermissions => Right(Ok()))
      .recover {
        case _: EntityNotFoundException =>
          Left(CollectionNotFoundError())
        case _ =>
          Left(UnknownError())
      }
      .foreach(replyTo ! SetCollectionPermissionsForUserResponse(_))
  }

  def onRemoveCollectionPermissionsForUser(msg: RemoveCollectionPermissionsForUserRequest): Unit = {
    val RemoveCollectionPermissionsForUserRequest(collectionId, userId, replyTo) = msg
    collectionPermissionsStore
      .removeCollectionUserPermissions(collectionId, userId)
      .map(_ => Right(Ok()))
      .recover {
        case _: EntityNotFoundException =>
          Left(CollectionNotFoundError())
        case _ =>
          Left(UnknownError())
      }
      .foreach(replyTo ! RemoveCollectionPermissionsForUserResponse(_))
  }
}

object CollectionStoreActor {
  def apply(collectionStore: CollectionStore, collectionPermissionsStore: CollectionPermissionsStore): Behavior[Message] =
    Behaviors.setup(new CollectionStoreActor(_, collectionStore, collectionPermissionsStore))

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
    new JsonSubTypes.Type(value = classOf[UpdateCollectionRequest], name = "update_collection"),
    new JsonSubTypes.Type(value = classOf[GetCollectionPermissionsRequest], name = "update_collection_permissions"),
    new JsonSubTypes.Type(value = classOf[GetCollectionWorldPermissionsRequest], name = "get_collection_world_perms"),
    new JsonSubTypes.Type(value = classOf[SetCollectionWorldPermissionsRequest], name = "set_collection_world_perms"),
    new JsonSubTypes.Type(value = classOf[GetCollectionUserPermissionsRequest], name = "get_collection_user_perms"),
    new JsonSubTypes.Type(value = classOf[GetCollectionUserPermissionsRequest], name = "get_collection_user_perms"),
    new JsonSubTypes.Type(value = classOf[GetCollectionPermissionsForUserRequest], name = "get_collection_perms_for_user"),
    new JsonSubTypes.Type(value = classOf[SetCollectionPermissionsForUserRequest], name = "set_collection_perms_for_user"),
    new JsonSubTypes.Type(value = classOf[RemoveCollectionPermissionsForUserRequest], name = "remove_collection_perms_for_user"),
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

  ////
  //// Permissions
  ////

  //
  // GetCollectionPermissions
  //
  final case class GetCollectionPermissionsRequest(collectionId: String, replyTo: ActorRef[GetCollectionPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetCollectionPermissionsError

  final case class GetCollectionPermissionsResponse(permissions: Either[GetCollectionPermissionsError, CollectionWorldAndUserPermissions]) extends CborSerializable

  //
  // GetCollectionWorldPermissions
  //
  final case class GetCollectionWorldPermissionsRequest(collectionId: String, replyTo: ActorRef[GetCollectionWorldPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetCollectionWorldPermissionsError

  final case class GetCollectionWorldPermissionsResponse(permissions: Either[GetCollectionWorldPermissionsError, CollectionPermissions]) extends CborSerializable

  //
  // SetCollectionWorldPermissionsRequest
  //
  final case class SetCollectionWorldPermissionsRequest(collectionId: String, permissions: CollectionPermissions, replyTo: ActorRef[SetCollectionWorldPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetCollectionWorldPermissionsError

  final case class SetCollectionWorldPermissionsResponse(response: Either[SetCollectionWorldPermissionsError, Ok]) extends CborSerializable

  //
  // GetAllCollectionUserPermissions
  //
  final case class GetCollectionUserPermissionsRequest(collectionId: String, replyTo: ActorRef[GetCollectionUserPermissionsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetCollectionUserPermissionsError

  final case class GetCollectionUserPermissionsResponse(permissions: Either[GetCollectionUserPermissionsError, Map[DomainUserId, CollectionPermissions]]) extends CborSerializable


  //
  // GetCollectionPermissionsForUser
  //
  case class GetCollectionPermissionsForUserRequest(collectionId: String, userId: DomainUserId, replyTo: ActorRef[GetCollectionPermissionsForUserResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetCollectionPermissionsForUserError

  final case class GetCollectionPermissionsForUserResponse(permissions: Either[GetCollectionPermissionsForUserError, CollectionPermissions]) extends CborSerializable

  //
  // SetCollectionPermissionsForUser
  //
  final case class SetCollectionPermissionsForUserRequest(collectionId: String, userId: DomainUserId, permissions: CollectionPermissions, replyTo: ActorRef[SetCollectionPermissionsForUserResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetCollectionPermissionsForUserError

  final case class SetCollectionPermissionsForUserResponse(response: Either[SetCollectionPermissionsForUserError, Ok]) extends CborSerializable

  //
  // RemoveCollectionPermissionsForUser
  //
  final case class RemoveCollectionPermissionsForUserRequest(collectionId: String, userId: DomainUserId, replyTo: ActorRef[RemoveCollectionPermissionsForUserResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[CollectionNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait RemoveCollectionPermissionsForUserError

  final case class RemoveCollectionPermissionsForUserResponse(response: Either[RemoveCollectionPermissionsForUserError, Ok]) extends CborSerializable

  //
  // Common Errors
  //

  final case class CollectionNotFoundError() extends AnyRef
    with GetCollectionError
    with DeleteCollectionError
    with UpdateCollectionError
    with GetCollectionPermissionsError
    with GetCollectionWorldPermissionsError
    with SetCollectionWorldPermissionsError
    with GetCollectionUserPermissionsError
    with SetCollectionPermissionsForUserError
    with GetCollectionPermissionsForUserError
    with RemoveCollectionPermissionsForUserError

  final case class UnknownError() extends AnyRef
    with GetCollectionsError
    with GetCollectionSummariesError
    with GetCollectionError
    with DeleteCollectionError
    with CreateCollectionError
    with UpdateCollectionError
    with GetCollectionPermissionsError
    with SetCollectionWorldPermissionsError
    with GetCollectionWorldPermissionsError
    with GetCollectionUserPermissionsError
    with SetCollectionPermissionsForUserError
    with GetCollectionPermissionsForUserError
    with RemoveCollectionPermissionsForUserError

}
