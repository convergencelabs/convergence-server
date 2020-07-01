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

package com.convergencelabs.convergence.server.api.rest.domain

import java.time.Instant
import java.util.UUID

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive._
import akka.http.scaladsl.server.Directives.{Segment, _}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.backend.datastore.domain.model.ModelDataGenerator
import com.convergencelabs.convergence.server.backend.services.domain.model._
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor.DomainRestMessage
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.model.{ModelMetaData, ModelPermissions, ModelPermissionsData, ModelUserPermissions}
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import com.convergencelabs.convergence.server.util.{DataValueToJValue, QueryLimit, QueryOffset}
import org.json4s.JsonAST.JObject

import scala.concurrent.{ExecutionContext, Future}

private[domain] final class DomainModelService(domainRestActor: ActorRef[DomainRestActor.Message],
                                               modelClusterRegion: ActorRef[RealtimeModelActor.Message],
                                               scheduler: Scheduler,
                                               executionContext: ExecutionContext,
                                               timeout: Timeout)
  extends AbstractDomainRestService(scheduler, executionContext, timeout) {

  import DomainModelService._

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("models") {
      pathEnd {
        get {
          parameters("offset".as[Int].?, "limit".as[Int].?) { (offset, limit) =>
            complete(getModels(domain, offset, limit))
          }
        } ~ post {
          entity(as[ModelPost]) { modelPost =>
            complete(postModel(domain, modelPost))
          }
        }
      } ~ pathPrefix(Segment) { modelId: String =>
        pathEnd {
          get {
            parameters("data".as[Boolean].?) { data =>
              complete(getModel(domain, modelId, data.getOrElse(true)))
            }
          } ~ put {
            entity(as[ModelPut]) { modelPut =>
              complete(putModel(domain, modelId, modelPut))
            }
          } ~ delete {
            complete(deleteModel(domain, modelId))
          }
        } ~ pathPrefix("permissions") {
          pathEnd {
            get {
              complete(getModelPermissions(domain, modelId))
            }
          } ~ pathPrefix("override") {
            pathEnd {
              get {
                complete(getModelOverridesPermissions(domain, modelId))
              } ~ put {
                entity(as[SetOverrideWorldRequest]) { overridesPermissions =>
                  complete(setModelOverridesPermissions(domain, modelId, overridesPermissions))
                }
              }
            }
          } ~ pathPrefix("world") {
            pathEnd {
              get {
                complete(getModelWorldPermissions(domain, modelId))
              } ~ put {
                entity(as[ModelPermissions]) { permissions =>
                  complete(setModelWorldPermissions(domain, modelId, permissions))
                }
              }
            }
          } ~ pathPrefix("user") {
            pathEnd {
              get {
                complete(getAllModelUserPermissions(domain, modelId))
              }
            } ~ pathPrefix(Segment) { user: String =>
              pathEnd {
                get {
                  complete(getModelUserPermissions(domain, modelId, user))
                } ~ put {
                  entity(as[ModelPermissions]) { permissions =>
                    complete(setModelUserPermissions(domain, modelId, user, permissions))
                  }
                } ~ delete {
                  complete(removeModelUserPermissions(domain, modelId, user))
                }
              }
            }
          }
        }
      }
    } ~ path("model-query") {
      post {
        entity(as[ModelQueryPost]) { query =>
          complete(queryModels(authProfile, domain, query))
        }
      }
    }
  }

  private[this] def getModels(domain: DomainId, offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    domainRestActor
      .ask[ModelStoreActor.GetModelsResponse](r => DomainRestMessage(domain,
        ModelStoreActor.GetModelsRequest(QueryOffset(offset.getOrElse(0)), QueryLimit(limit.getOrElse(10)), r)))
      .map(_.models.fold(
        _ => InternalServerError,
        models => okResponse(models.map(mapMetaData))
      ))
  }

  private[this] def getModel(domain: DomainId, modelId: String, data: Boolean): Future[RestResponse] = {
    modelClusterRegion
      .ask[RealtimeModelActor.GetRealtimeModelResponse](r => RealtimeModelActor.GetRealtimeModelRequest(domain, modelId, None, r))
      .map(_.model.fold(
        {
          case RealtimeModelActor.ModelNotFoundError() =>
            notFound(modelId)
          case RealtimeModelActor.UnauthorizedError(_) =>
            ForbiddenError
          case RealtimeModelActor.UnknownError() =>
            InternalServerError
        },
        { model =>
          val result = if (data) {
            ModelData(
              model.metaData.id,
              model.metaData.collection,
              model.metaData.version,
              model.metaData.createdTime,
              model.metaData.modifiedTime,
              DataValueToJValue.toJson(model.data).asInstanceOf[JObject])
          } else {
            ModelMetaDataResponse(
              model.metaData.id,
              model.metaData.collection,
              model.metaData.version,
              model.metaData.createdTime,
              model.metaData.modifiedTime)
          }
          okResponse(result)
        }
      ))
  }

  private[this] def postModel(domain: DomainId, model: ModelPost): Future[RestResponse] = {
    val ModelPost(collectionId, data, overrideWorld, worldPermissions, userPermissions) = model
    val modelId = UUID.randomUUID().toString
    val objectValue = ModelDataGenerator(data)
    val userPerms = userPermissions.getOrElse(Map()).map { case (username, perms) =>
      DomainUserId.normal(username) -> perms
    }

    modelClusterRegion.ask[RealtimeModelActor.CreateRealtimeModelResponse](r =>
      RealtimeModelActor.CreateRealtimeModelRequest(domain, modelId, collectionId, objectValue, overrideWorld, worldPermissions, userPerms, None, r))
      .map(_.response.fold(
        {
          case RealtimeModelActor.ModelAlreadyExistsError() =>
            conflictsResponse("modelId", modelId)
          case RealtimeModelActor.UnauthorizedError(_) =>
            ForbiddenError
          case RealtimeModelActor.UnknownError() =>
            InternalServerError
          case RealtimeModelActor.InvalidCreationDataError(message) =>
            badRequest(message)
        },
        { modelId =>
          createdResponse(CreateModelResponse(modelId))
        }
      ))
  }

  private[this] def putModel(domain: DomainId, modelId: String, modelPut: ModelPut): Future[RestResponse] = {
    val ModelPut(collectionId, data, overrideWorld, worldPermissions, userPermissions) = modelPut
    val objectValue = ModelDataGenerator(data)
    val userPerms = userPermissions.getOrElse(Map()).map { case (username, perms) =>
      DomainUserId.normal(username) -> perms
    }

    modelClusterRegion.ask[RealtimeModelActor.CreateOrUpdateRealtimeModelResponse](r =>
      RealtimeModelActor.CreateOrUpdateRealtimeModelRequest(domain, modelId, collectionId, objectValue, overrideWorld, worldPermissions, userPerms, None, r))
      .map(_.response.fold(
        {
          case RealtimeModelActor.UnauthorizedError(_) =>
            ForbiddenError
          case RealtimeModelActor.UnknownError() =>
            InternalServerError
          case RealtimeModelActor.ModelOpenError() =>
            badRequest("The model is currently open and can therefore not be updated.")
        },
        _ => OkResponse
      ))
  }

  private[this] def queryModels(authProfile: AuthorizationProfile, domain: DomainId, queryPost: ModelQueryPost): Future[RestResponse] = {
    val ModelQueryPost(query) = queryPost
    val userId = DomainUserId.convergence(authProfile.username)
    domainRestActor.ask[ModelStoreActor.QueryModelsResponse](r =>
      DomainRestMessage(domain, ModelStoreActor.QueryModelsRequest(userId, query, r)))
      .map(_.result.fold(
        {
          case ModelStoreActor.InvalidQueryError(message, query, index) =>
            val details = if (index.isDefined) {
              Map("query" -> query, "index" -> index.get)
            } else {
              Map("query" -> query)
            }
            badRequest(message, Some(details))

          case ModelStoreActor.UnknownError() =>
            InternalServerError
        },
        { results =>
          val models = results.data.map(model => ModelData(
            model.metaData.id,
            model.metaData.collection,
            model.metaData.version,
            model.metaData.createdTime,
            model.metaData.modifiedTime,
            model.data))
          val response = PagedRestResponse(models, results.offset, results.count)
          okResponse(response)
        }
      ))
  }

  private[this] def deleteModel(domain: DomainId, modelId: String): Future[RestResponse] = {
    modelClusterRegion.ask[RealtimeModelActor.DeleteRealtimeModelResponse](RealtimeModelActor.DeleteRealtimeModelRequest(domain, modelId, None, _))
      .map(_.response.fold(
        {
          case RealtimeModelActor.ModelNotFoundError() =>
            notFound(modelId)
          case RealtimeModelActor.UnauthorizedError(_) =>
            ForbiddenError
          case RealtimeModelActor.UnknownError() =>
            InternalServerError
        },
        _ => DeletedResponse
      ))
  }

  // Model Permissions

  private[this] def getModelOverridesPermissions(domain: DomainId, modelId: String): Future[RestResponse] = {
    domainRestActor
      .ask[ModelPermissionsStoreActor.GetModelOverridesPermissionsResponse](
        r => DomainRestMessage(domain, ModelPermissionsStoreActor.GetModelOverridesPermissionsRequest(modelId, r)))
      .map(_.overrides.fold(
        {
          case ModelPermissionsStoreActor.ModelNotFoundError() =>
            modelNotFound()
          case ModelPermissionsStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => okResponse(GetModelOverridesPermissionsResponse(_))
      ))
  }

  private[this] def setModelOverridesPermissions(domain: DomainId, modelId: String, overridesPermissions: SetOverrideWorldRequest): Future[RestResponse] = {
    val SetOverrideWorldRequest(overrideWorld) = overridesPermissions
    domainRestActor
      .ask[ModelPermissionsStoreActor.SetModelOverridesPermissionsResponse](
        r => DomainRestMessage(domain, ModelPermissionsStoreActor.SetModelOverridesPermissionsRequest(modelId, overrideWorld, r)))
      .map(_.response.fold(
        {
          case ModelPermissionsStoreActor.ModelNotFoundError() =>
            modelNotFound()
          case ModelPermissionsStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def getModelPermissions(domain: DomainId, modelId: String): Future[RestResponse] = {
    domainRestActor
      .ask[ModelPermissionsStoreActor.GetModelPermissionsResponse](
        r => DomainRestMessage(domain, ModelPermissionsStoreActor.GetModelPermissionsRequest(modelId, r)))
      .map(_.permissions.fold(
        {
          case ModelPermissionsStoreActor.ModelNotFoundError() =>
            modelNotFound()
          case ModelPermissionsStoreActor.UnknownError() =>
            InternalServerError
        },
        { permissions =>
          val ModelPermissionsData(overridePermissions, world, users) = permissions
          okResponse(ModelPermissionsSummary(overridePermissions, world, users))
        }
      ))
  }

  private[this] def getModelWorldPermissions(domain: DomainId, modelId: String): Future[RestResponse] = {
    domainRestActor
      .ask[ModelPermissionsStoreActor.GetModelWorldPermissionsResponse](
        r => DomainRestMessage(domain, ModelPermissionsStoreActor.GetModelWorldPermissionsRequest(modelId, r)))
      .map(_.permissions.fold(
        {
          case ModelPermissionsStoreActor.ModelNotFoundError() =>
            modelNotFound()
          case ModelPermissionsStoreActor.UnknownError() =>
            InternalServerError
        },
        permissions => okResponse(GetPermissionsResponseData(Some(permissions)))
      ))
  }

  private[this] def setModelWorldPermissions(domain: DomainId, modelId: String, permissions: ModelPermissions): Future[RestResponse] = {
    domainRestActor
      .ask[ModelPermissionsStoreActor.SetModelWorldPermissionsResponse](
        r => DomainRestMessage(domain, ModelPermissionsStoreActor.SetModelWorldPermissionsRequest(modelId, permissions, r)))
      .map(_.response.fold(
        {
          case ModelPermissionsStoreActor.ModelNotFoundError() =>
            modelNotFound()
          case ModelPermissionsStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def getAllModelUserPermissions(domain: DomainId, modelId: String): Future[RestResponse] = {
    domainRestActor
      .ask[ModelPermissionsStoreActor.GetAllModelUserPermissionsResponse](
        r => DomainRestMessage(domain, ModelPermissionsStoreActor.GetAllModelUserPermissionsRequest(modelId, r)))
      .map(_.permissions.fold(
        {
          case ModelPermissionsStoreActor.ModelNotFoundError() =>
            modelNotFound()
          case ModelPermissionsStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => okResponse(GetAllUserPermissionsResponseData(_))
      ))
  }

  private[this] def getModelUserPermissions(domain: DomainId, modelId: String, username: String): Future[RestResponse] = {
    domainRestActor
      .ask[ModelPermissionsStoreActor.GetModelUserPermissionsResponse](
        r => DomainRestMessage(domain, ModelPermissionsStoreActor.GetModelUserPermissionsRequest(modelId, DomainUserId.normal(username), r)))
      .map(_.permissions.fold(
        {
          case ModelPermissionsStoreActor.ModelNotFoundError() =>
            modelNotFound()
          case ModelPermissionsStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => okResponse(GetPermissionsResponseData(_))
      ))
  }

  private[this] def setModelUserPermissions(domain: DomainId, modelId: String, username: String, permissions: ModelPermissions): Future[RestResponse] = {
    domainRestActor
      .ask[ModelPermissionsStoreActor.SetModelUserPermissionsResponse](
        r => DomainRestMessage(domain, ModelPermissionsStoreActor.SetModelUserPermissionsRequest(modelId, DomainUserId.normal(username), permissions, r)))
      .map(_.response.fold(
        {
          case ModelPermissionsStoreActor.ModelNotFoundError() =>
            modelNotFound()
          case ModelPermissionsStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def removeModelUserPermissions(domain: DomainId, modelId: String, username: String): Future[RestResponse] = {
    domainRestActor
      .ask[ModelPermissionsStoreActor.RemoveModelUserPermissionsResponse](
        r => DomainRestMessage(domain, ModelPermissionsStoreActor.RemoveModelUserPermissionsRequest(modelId, DomainUserId.normal(username), r)))
      .map(_.response.fold(
        {
          case ModelPermissionsStoreActor.ModelNotFoundError() =>
            modelNotFound()
          case ModelPermissionsStoreActor.UnknownError() =>
            InternalServerError
        },
        _ => OkResponse
      ))
  }

  private[this] def modelNotFound(): RestResponse = notFoundResponse("The specified model does not exist")

  private[this] def mapMetaData(metaData: ModelMetaData): ModelMetaDataResponse = {
    ModelMetaDataResponse(
      metaData.id,
      metaData.collection,
      metaData.version,
      metaData.createdTime,
      metaData.modifiedTime)
  }

  private[this] def notFound(modelId: String): RestResponse = {
    notFoundResponse(Some(s"A model with id '$modelId' does not exist."))
  }
}


object DomainModelService {

  final case class ModelPost(collection: String,
                             data: Map[String, Any],
                             overrideWorld: Option[Boolean],
                             worldPermissions: Option[ModelPermissions],
                             userPermissions: Option[Map[String, ModelPermissions]])

  final case class ModelPut(collection: String,
                            data: Map[String, Any],
                            overrideWorld: Option[Boolean],
                            worldPermissions: Option[ModelPermissions],
                            userPermissions: Option[Map[String, ModelPermissions]]
                           )

  final case class ModelMetaDataResponse(id: String,
                                         collection: String,
                                         version: Long,
                                         createdTime: Instant,
                                         modifiedTime: Instant)

  final case class ModelData(id: String,
                             collection: String,
                             version: Long,
                             createdTime: Instant,
                             modifiedTime: Instant,
                             data: JObject)

  final case class CreateModelResponse(id: String)

  final case class ModelPermissionsSummary(overrideWorld: Boolean,
                                           worldPermissions: ModelPermissions,
                                           userPermissions: List[ModelUserPermissions])

  final case class GetPermissionsResponseData(permissions: Option[ModelPermissions])

  final case class GetAllUserPermissionsResponseData(userPermissions: List[ModelUserPermissions])

  final case class GetModelOverridesPermissionsResponse(overrideWorld: Boolean)

  final case class SetOverrideWorldRequest(overrideWorld: Boolean)

  final case class ModelQueryPost(query: String)

}
