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
import com.convergencelabs.convergence.server.domain.model.{Model, ModelMetaData, ModelQueryResult}
import com.convergencelabs.convergence.server.domain.{DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import grizzled.slf4j.Logging

import scala.util.Success

class ModelStoreActor private[datastore](context: ActorContext[ModelStoreActor.Message],
                                         private[this] val persistenceProvider: DomainPersistenceProvider)
  extends AbstractBehavior[ModelStoreActor.Message](context) with Logging {

  import ModelStoreActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case msg: GetModelsRequest =>
        onGetModels(msg)
      case msg: GetModelsInCollectionRequest =>
        onGetModelsInCollection(msg)
      case message: QueryModelsRequest =>
        onQueryModelsRequest(message)
      case message: GetModelUpdateRequest =>
        onGetModelUpdate(message)
    }

    Behaviors.same
  }

  private[this] def onGetModels(msg: GetModelsRequest): Unit = {
    val GetModelsRequest(offset, limit, replyTo) = msg
    persistenceProvider.modelStore.getAllModelMetaData(offset, limit)
      .map(models => GetModelsResponse(Right(models)))
      .recover { cause =>
        error("Unexpected error getting models", cause)
        GetModelsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetModelsInCollection(msg: GetModelsInCollectionRequest): Unit = {
    val GetModelsInCollectionRequest(collectionId, offset, limit, replyTo) = msg
    persistenceProvider.modelStore.getAllModelMetaDataInCollection(collectionId, offset, limit)
      .map(models => GetModelsInCollectionResponse(Right(models)))
      .recover { cause =>
        error("Unexpected error getting models in collection", cause)
        GetModelsInCollectionResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onQueryModelsRequest(request: QueryModelsRequest): Unit = {
    val QueryModelsRequest(userId, query, replyTo) = request
    val uid: Option[DomainUserId] = if (userId.userType == DomainUserType.Convergence) {
      None
    } else {
      Some(userId)
    }

    persistenceProvider.modelStore.queryModels(query, uid)
      .map(results => QueryModelsResponse(Right(results)))
      .recover {
        case QueryParsingException(message, query, index) =>
          QueryModelsResponse(Left(InvalidQueryError(message, query, index)))
        case cause =>
          error("Unexpected error getting models in collection", cause)
          QueryModelsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetModelUpdate(request: GetModelUpdateRequest): Unit = {
    val GetModelUpdateRequest(modelId, currentVersion, currentPermissions, userId, replyTo) = request
    persistenceProvider.modelPermissionsStore.getUsersCurrentModelPermissions(modelId, userId).flatMap {
      case Some(permissions) =>
        if (permissions.read) {
          // Model still exists and is still readable. Check to see if the
          // permissions or model need to be updated.
          val permissionsUpdate = if (permissions == currentPermissions) {
            None
          } else {
            Some(permissions)
          }

          if (currentVersion == 0) {
            // Initial request
            persistenceProvider.modelStore.getModel(modelId) flatMap {
              case Some(model) =>
                persistenceProvider.modelStore.getAndIncrementNextValuePrefix(modelId).map { prefix =>
                  OfflineModelInitial(model, permissions, prefix)
                }
              case None =>
                warn("A model was deleted during an update request")
                Success(OfflineModelDeleted())
            }
          } else {
            persistenceProvider.modelStore.getModelIfNewer(modelId, currentVersion) map { modelUpdate =>
              (permissionsUpdate, modelUpdate) match {
                case (None, None) =>
                  // No update to permissions or model.
                  OfflineModelNotUpdate()
                case (p, m) =>
                  // At least one is different.
                  OfflineModelUpdated(m, p)
              }
            }
          }
        } else {
          // This means the permissions were changed and now this
          // user does not have read permissions. We don't even
          // bother to look up the model.
          Success(OfflineModelPermissionRevoked())
        }
      case None =>
        // Model doesn't exist anymore
        Success(OfflineModelDeleted())
    }
      .map(action => GetModelUpdateResponse(Right(action)))
      .recover {
        case cause: Throwable =>
          error("Unexpected error processing model update request", cause)
          GetModelUpdateResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }
}

object ModelStoreActor {
  def apply(persistenceProvider: DomainPersistenceProvider): Behavior[Message] = Behaviors.setup { context =>
    new ModelStoreActor(context, persistenceProvider)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GetModelsRequest], name = "get_models"),
    new JsonSubTypes.Type(value = classOf[GetModelUpdateRequest], name = "get_model_update"),
    new JsonSubTypes.Type(value = classOf[GetModelsInCollectionRequest], name = "get_models_in_collection"),
    new JsonSubTypes.Type(value = classOf[QueryModelsRequest], name = "query_models"),
  ))
  sealed trait Message extends CborSerializable

  //
  // GetModels
  //
  final case class GetModelsRequest(offset: QueryOffset,
                                    limit: QueryLimit,
                                    replyTo: ActorRef[GetModelsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetModelsError

  final case class GetModelsResponse(models: Either[GetModelsError, List[ModelMetaData]]) extends CborSerializable

  //
  // GetModelsInCollection
  //
  final case class GetModelsInCollectionRequest(collectionId: String,
                                                offset: QueryOffset,
                                                limit: QueryLimit,
                                                replyTo: ActorRef[GetModelsInCollectionResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetModelsInCollectionError

  final case class GetModelsInCollectionResponse(models: Either[GetModelsInCollectionError, List[ModelMetaData]]) extends CborSerializable

  //
  // Query Models
  //
  final case class QueryModelsRequest(userId: DomainUserId, query: String, replyTo: ActorRef[QueryModelsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[InvalidQueryError], name = "invalid_query"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait QueryModelsError

  final case class InvalidQueryError(message: String, query: String, index: Option[Int]) extends QueryModelsError

  final case class QueryModelsResponse(result: Either[QueryModelsError, PagedData[ModelQueryResult]]) extends CborSerializable

  //
  // GetModelUpdate
  //
  final case class GetModelUpdateRequest(modelId: String,
                                         currentVersion: Long,
                                         currentPermissions: ModelPermissions,
                                         userId: DomainUserId,
                                         replyTo: ActorRef[GetModelUpdateResponse]) extends Message


  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetModelUpdateError


  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[OfflineModelPermissionRevoked], name = "permissions_revoked"),
    new JsonSubTypes.Type(value = classOf[OfflineModelNotUpdate], name = "not_updated"),
    new JsonSubTypes.Type(value = classOf[OfflineModelDeleted], name = "deleted"),
    new JsonSubTypes.Type(value = classOf[OfflineModelUpdated], name = "updated"),
    new JsonSubTypes.Type(value = classOf[OfflineModelInitial], name = "initial"),
  ))
  sealed trait ModelUpdateResult

  final case class OfflineModelPermissionRevoked() extends ModelUpdateResult

  final case class OfflineModelNotUpdate() extends ModelUpdateResult

  final case class OfflineModelDeleted() extends ModelUpdateResult

  final case class OfflineModelUpdated(model: Option[Model], permissions: Option[ModelPermissions]) extends ModelUpdateResult

  final case class OfflineModelInitial(model: Model, permissions: ModelPermissions, valueIdPrefix: Long) extends ModelUpdateResult


  final case class GetModelUpdateResponse(result: Either[GetModelUpdateError, ModelUpdateResult]) extends CborSerializable

  //
  // Common Errors
  //

  final case class ModelNotFoundError() extends AnyRef
    with GetModelsError

  final case class UnknownError() extends AnyRef
    with QueryModelsError
    with GetModelsInCollectionError
    with GetModelsError
    with GetModelUpdateError

}
