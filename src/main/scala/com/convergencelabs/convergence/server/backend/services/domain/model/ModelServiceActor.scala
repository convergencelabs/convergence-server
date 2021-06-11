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

package com.convergencelabs.convergence.server.backend.services.domain.model

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore.domain.model.QueryParsingException
import com.convergencelabs.convergence.server.backend.services.domain.{DomainPersistenceManager, BaseDomainShardedActor}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.model.{Model, ModelMetaData, ModelPermissions}
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.concurrent.duration.FiniteDuration
import scala.util.Success

private final class ModelServiceActor(domainId: DomainId,
                                      context: ActorContext[ModelServiceActor.Message],
                                      shardRegion: ActorRef[ModelServiceActor.Message],
                                      shard: ActorRef[ClusterSharding.ShardCommand],
                                      domainPersistenceManager: DomainPersistenceManager,
                                      receiveTimeout: FiniteDuration)
  extends BaseDomainShardedActor[ModelServiceActor.Message](domainId, context, shardRegion, shard, domainPersistenceManager, receiveTimeout) {

  import ModelServiceActor._

  override protected def receiveInitialized(msg: Message): Behavior[Message] = {
    msg match {
      case msg: GetModelsRequest =>
        onGetModels(msg)
      case msg: GetModelsInCollectionRequest =>
        onGetModelsInCollection(msg)
      case message: QueryModelsRequest =>
        onQueryModelsRequest(message)
      case message: GetModelUpdateRequest =>
        onGetModelUpdate(message)
      case ReceiveTimeout(_) =>
        this.passivate()
    }
  }

  private[this] def onGetModels(msg: GetModelsRequest): Behavior[Message] = {
    val GetModelsRequest(_, offset, limit, replyTo) = msg
    persistenceProvider.modelStore.getAllModelMetaData(offset, limit)
      .map(models => Right(models))
      .recover { cause =>
        error("Unexpected error getting models", cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! GetModelsResponse(_))

    Behaviors.same
  }

  private[this] def onGetModelsInCollection(msg: GetModelsInCollectionRequest): Behavior[Message] = {
    val GetModelsInCollectionRequest(_, collectionId, offset, limit, replyTo) = msg
    persistenceProvider.modelStore.getAllModelMetaDataInCollection(collectionId, offset, limit)
      .map(models => Right(models))
      .recover { cause =>
        error("Unexpected error getting models in collection", cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! GetModelsInCollectionResponse(_))

    Behaviors.same
  }

  private[this] def onQueryModelsRequest(request: QueryModelsRequest): Behavior[Message] = {
    val QueryModelsRequest(_, userId, query, replyTo) = request
    val uid: Option[DomainUserId] = if (userId.userType == DomainUserType.Convergence) {
      None
    } else {
      Some(userId)
    }

    persistenceProvider.modelStore.queryModels(query, uid)
      .map(results => Right(results))
      .recover {
        case QueryParsingException(message, query, index) =>
          Left(InvalidQueryError(message, query, index))
        case cause =>
          error("Unexpected error getting models in collection", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! QueryModelsResponse(_))

    Behaviors.same
  }

  private[this] def onGetModelUpdate(request: GetModelUpdateRequest): Behavior[Message] = {
    val GetModelUpdateRequest(_, modelId, currentVersion, currentPermissions, userId, replyTo) = request
    persistenceProvider.modelPermissionCalculator.getUsersCurrentModelPermissions(modelId, userId).flatMap {
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
      .map(action => Right(action))
      .recover {
        case cause: Throwable =>
          error("Unexpected error processing model update request", cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetModelUpdateResponse(_))

    Behaviors.same
  }

  override protected def getDomainId(msg: Message): DomainId = msg.domainId

  override protected def getReceiveTimeoutMessage(): Message = ReceiveTimeout(this.domainId)
}

object ModelServiceActor {
  def apply(domainId: DomainId,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration): Behavior[Message] = Behaviors.setup { context =>
    new ModelServiceActor(
      domainId,
      context,
      shardRegion,
      shard,
      domainPersistenceManager,
      receiveTimeout)
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
  sealed trait Message extends CborSerializable {
    def domainId: DomainId
  }

  private final case class ReceiveTimeout(domainId: DomainId) extends Message

  //
  // GetModels
  //
  final case class GetModelsRequest(domainId: DomainId,
                                    @JsonDeserialize(contentAs = classOf[Long])
                                    offset: QueryOffset,
                                    @JsonDeserialize(contentAs = classOf[Long])
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
  final case class GetModelsInCollectionRequest(domainId: DomainId,
                                                collectionId: String,
                                                @JsonDeserialize(contentAs = classOf[Long])
                                                offset: QueryOffset,
                                                @JsonDeserialize(contentAs = classOf[Long])
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
  final case class QueryModelsRequest(domainId: DomainId,
                                      userId: DomainUserId,
                                      query: String,
                                      replyTo: ActorRef[QueryModelsResponse]) extends Message

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
  final case class GetModelUpdateRequest(domainId: DomainId,
                                         modelId: String,
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
