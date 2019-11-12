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

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.datastore.domain.{ModelDataGenerator, ModelPermissions}
import com.convergencelabs.convergence.server.datastore.domain.ModelPermissionsStoreActor._
import com.convergencelabs.convergence.server.datastore.domain.ModelStoreActor.{GetModels, GetModelsInCollection, QueryModelsRequest}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId}
import com.convergencelabs.convergence.server.domain.model._
import com.convergencelabs.convergence.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import org.json4s.JsonAST.JObject

import scala.concurrent.{ExecutionContext, Future}

object DomainModelService {

  case class ModelPost(collection: String,
                       data: Map[String, Any],
                       overrideWorld: Option[Boolean],
                       worldPermissions: Option[ModelPermissions],
                       userPermissions: Option[Map[String, ModelPermissions]])

  case class ModelPut(collection: String,
                      data: Map[String, Any],
                      overrideWorld: Option[Boolean],
                      worldPermissions: Option[ModelPermissions],
                      userPermissions: Option[Map[String, ModelPermissions]]
                     )

  case class ModelMetaDataResponse(id: String,
                                   collection: String,
                                   version: Long,
                                   createdTime: Instant,
                                   modifiedTime: Instant)

  case class ModelData(id: String,
                       collection: String,
                       version: Long,
                       createdTime: Instant,
                       modifiedTime: Instant,
                       data: JObject)

  case class CreateModelResponse(id: String)

  case class ModelPermissionsSummary(overrideWorld: Boolean, worldPermissions: ModelPermissions, userPermissions: List[ModelUserPermissions])

  case class GetPermissionsResponse(permissions: Option[ModelPermissions])

  case class GetAllUserPermissionsResponse(userPermissions: List[ModelUserPermissions])

  case class GetModelOverridesPermissionsResponse(overrideWorld: Boolean)

  case class SetOverrideWorldRequest(overrideWorld: Boolean)

  case class ModelQueryPost(query: String, offset: Option[Int], limit: Option[Int])

}

class DomainModelService(private[this] val executionContext: ExecutionContext,
                         private[this] val timeout: Timeout,
                         private[this] val domainRestActor: ActorRef,
                         private[this] val modelClusterRegion: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainModelService._
  import akka.http.scaladsl.server.Directive._
  import akka.http.scaladsl.server.Directives.{Segment, _}
  import akka.pattern.ask

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("models") {
      pathEnd {
        get {
          complete(getModels(domain))
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

  def getModels(domain: DomainId): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetModels(None, None))
    (domainRestActor ? message).mapTo[List[ModelMetaData]] map {
      _.map(mapMetaData)
    } map {
      models => okResponse(models)
    }
  }

  def getModelsInCollection(domain: DomainId, collectionId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetModelsInCollection(collectionId, None, None))
    (domainRestActor ? message).mapTo[List[ModelMetaData]] map {
      _.map(mapMetaData)
    } map {
      models => okResponse(models)
    }
  }

  def mapMetaData(metaData: ModelMetaData): ModelMetaDataResponse = {
    ModelMetaDataResponse(
      metaData.id,
      metaData.collection,
      metaData.version,
      metaData.createdTime,
      metaData.modifiedTime)
  }

  def getModel(domain: DomainId, modelId: String, data: Boolean): Future[RestResponse] = {
    val message = GetRealtimeModel(domain, modelId, None)
    (modelClusterRegion ? message).mapTo[Option[Model]] map {
      case Some(model) =>
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
      case None =>
        notFound(modelId)
    }
  }

  def postModel(domain: DomainId, model: ModelPost): Future[RestResponse] = {
    val ModelPost(collectionId, data, overrideWorld, worldPermissions, userPermissions) = model
    val modelId = UUID.randomUUID().toString
    val objectValue = ModelDataGenerator(data)
    val userPerms = userPermissions.getOrElse(Map()).map { case (username, perms) =>
      DomainUserId.normal(username) -> perms
    }
    val message = CreateRealtimeModel(domain, modelId, collectionId, objectValue, overrideWorld, worldPermissions, userPerms, None)
    (modelClusterRegion ? message).mapTo[String] map {
      modelId: String => createdResponse(CreateModelResponse(modelId))
    }
  }

  def putModel(domain: DomainId, modelId: String, modelPut: ModelPut): Future[RestResponse] = {
    val ModelPut(collectionId, data, overrideWorld, worldPermissions, userPermissions) = modelPut
    val objectValue = ModelDataGenerator(data)
    val userPerms = userPermissions.getOrElse(Map()).map { case (username, perms) =>
      DomainUserId.normal(username) -> perms
    }
    val message = CreateOrUpdateRealtimeModel(domain, modelId, collectionId, objectValue, overrideWorld, worldPermissions, userPerms, None)
    (modelClusterRegion ? message) map { _ => OkResponse }
  }

  def queryModels(authProfile: AuthorizationProfile, domain: DomainId, queryPost: ModelQueryPost): Future[RestResponse] = {
    val ModelQueryPost(query, offset, limit) = queryPost
    val userId = DomainUserId.convergence(authProfile.username)
    val message = DomainRestMessage(domain, QueryModelsRequest(userId, query))
    (domainRestActor ? message)
      .mapTo[PagedData[ModelQueryResult]]
      .map { results =>
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
  }

  def deleteModel(domain: DomainId, modelId: String): Future[RestResponse] = {
    val message = DeleteRealtimeModel(domain, modelId, None)
    (modelClusterRegion ? message) map { _ => OkResponse } recover {
      case cause: ModelNotFoundException =>
        notFound(modelId)
    }
  }

  // Model Permissions

  def getModelOverridesPermissions(domain: DomainId, modelId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetModelOverridesPermissions(modelId))
    (domainRestActor ? message).mapTo[Boolean] map {
      overridesPermissions =>
        okResponse(GetModelOverridesPermissionsResponse(overridesPermissions))
    }
  }

  def setModelOverridesPermissions(domain: DomainId, modelId: String, overridesPermissions: SetOverrideWorldRequest): Future[RestResponse] = {
    val SetOverrideWorldRequest(overrideWorld) = overridesPermissions
    val message = DomainRestMessage(domain, SetModelOverridesPermissions(modelId, overrideWorld))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getModelPermissions(domain: DomainId, modelId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetModelPermissions(modelId))
    (domainRestActor ? message).mapTo[ModelPermissionsResponse] map {
      response =>
        val ModelPermissionsResponse(overridePermissions, world, users) = response
        okResponse(ModelPermissionsSummary(overridePermissions, world, users))
    }
  }

  def getModelWorldPermissions(domain: DomainId, modelId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetModelWorldPermissions(modelId))
    (domainRestActor ? message).mapTo[ModelPermissions] map {
      permissions =>
        okResponse(GetPermissionsResponse(Some(permissions)))
    }
  }

  def setModelWorldPermissions(domain: DomainId, modelId: String, permissions: ModelPermissions): Future[RestResponse] = {
    val message = DomainRestMessage(domain, SetModelWorldPermissions(modelId, permissions))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getAllModelUserPermissions(domain: DomainId, modelId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetAllModelUserPermissions(modelId))
    (domainRestActor ? message).mapTo[List[ModelUserPermissions]] map {
      permissions =>
        okResponse(GetAllUserPermissionsResponse(permissions))
    }
  }

  def getModelUserPermissions(domain: DomainId, modelId: String, username: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetModelUserPermissions(modelId, DomainUserId.normal(username)))
    (domainRestActor ? message).mapTo[Option[ModelPermissions]] map {
      permissions =>
        okResponse(GetPermissionsResponse(permissions))
    }
  }

  def setModelUserPermissions(domain: DomainId, modelId: String, username: String, permissions: ModelPermissions): Future[RestResponse] = {
    val message = DomainRestMessage(domain, SetModelUserPermissions(modelId, DomainUserId.normal(username), permissions))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def removeModelUserPermissions(domain: DomainId, modelId: String, username: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, RemoveModelUserPermissions(modelId, DomainUserId.normal(username)))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  private[this] def notFound(modelId: String): RestResponse = {
    notFoundResponse(Some(s"A model with id '$modelId' does not exist."))
  }
}
