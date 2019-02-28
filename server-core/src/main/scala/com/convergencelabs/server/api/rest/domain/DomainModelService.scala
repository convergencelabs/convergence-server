package com.convergencelabs.server.api.rest.domain

import java.time.Instant
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.api.rest.OkResponse
import com.convergencelabs.server.api.rest.RestResponse
import com.convergencelabs.server.api.rest.createdResponse
import com.convergencelabs.server.api.rest.notFoundResponse
import com.convergencelabs.server.api.rest.okResponse
import com.convergencelabs.server.datastore.domain.ModelDataGenerator
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.GetAllModelUserPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.GetModelOverridesPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.GetModelPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.GetModelUserPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.GetModelWorldPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.ModelPermissionsResponse
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.ModelUserPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.RemoveModelUserPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.SetModelOverridesPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.SetModelUserPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStoreActor.SetModelWorldPermissions
import com.convergencelabs.server.datastore.domain.ModelStoreActor.GetModels
import com.convergencelabs.server.datastore.domain.ModelStoreActor.GetModelsInCollection
import com.convergencelabs.server.datastore.domain.ModelStoreActor.QueryModelsRequest
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.model.CreateOrUpdateRealtimeModel
import com.convergencelabs.server.domain.model.CreateRealtimeModel
import com.convergencelabs.server.domain.model.DeleteRealtimeModel
import com.convergencelabs.server.domain.model.GetRealtimeModel
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.ModelNotFoundException
import com.convergencelabs.server.domain.model.ModelQueryResult
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.rest.RestDomainActor.DomainRestMessage
import com.convergencelabs.server.security.AuthorizationProfile

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Route
import akka.util.Timeout

object DomainModelService {

  case class ModelPost(
    collection: String,
    data: Map[String, Any])

  case class ModelPut(
      collection: String,
      data: Map[String, Any])

  case class ModelMetaDataResponse(
    id: String,
    collection: String,
    version: Long,
    createdTime: Instant,
    modifiedTime: Instant)

  case class ModelData(
    id: String,
    collection: String,
    version: Long,
    createdTime: Instant,
    modifiedTime: Instant,
    data: ObjectValue)

  case class CreateModelResponse(id: String)
  case class ModelPermissionsSummary(overrideWorld: Boolean, worldPermissions: ModelPermissions, userPermissions: List[ModelUserPermissions])
  case class GetPermissionsResponse(permissions: Option[ModelPermissions])
  case class GetAllUserPermissionsResponse(userPermissions: List[ModelUserPermissions])
  case class GetModelOverridesPermissionsResponse(overrideWorld: Boolean)
  case class SetOverrideWorldRequest(overrideWorld: Boolean)

  case class ModelQueryPost(query: String)
}

class DomainModelService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val domainRestActor: ActorRef,
  private[this] val modelClusterRegion: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainModelService._
  import akka.http.scaladsl.server.Directives.Segment
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
            complete(getModel(domain, modelId))
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
      _.map(mapMetaData(_))
    } map {
      models => okResponse(models)
    }
  }

  def getModelsInCollection(domain: DomainId, collectionId: String): Future[RestResponse] = {
    val message = DomainRestMessage(domain, GetModelsInCollection(collectionId, None, None))
    (domainRestActor ? message).mapTo[List[ModelMetaData]] map {
      _.map(mapMetaData(_))
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

  def getModel(domain: DomainId, modelId: String): Future[RestResponse] = {
    val message = GetRealtimeModel(domain, modelId, None)
    (modelClusterRegion ? message).mapTo[Option[Model]] map {
      case Some(model) =>
        val mr = ModelData(
          model.metaData.id,
          model.metaData.collection,
          model.metaData.version,
          model.metaData.createdTime,
          model.metaData.modifiedTime,
          model.data)
        okResponse(mr)
      case None =>
        notFound(modelId)
    }
  }

  def postModel(domain: DomainId, model: ModelPost): Future[RestResponse] = {
    val ModelPost(colletionId, data) = model

    // FIXME abstract this.
    val modelId = UUID.randomUUID().toString()
    val objectValue = ModelDataGenerator(data)
    // FIXME need to pass in model permissions options.
    val message = CreateRealtimeModel(domain, modelId, colletionId, objectValue, None, None, Map(), None)
    (modelClusterRegion ? message).mapTo[String] map {
      case modelId: String =>
        createdResponse(CreateModelResponse(modelId))
    }
  }

  def putModel(domain: DomainId, modelId: String, modelPut: ModelPut): Future[RestResponse] = {
    val ModelPut(colleciontId, data) = modelPut
    val objectValue = ModelDataGenerator(data)
    // FIXME need to pass id model permissions options.
    val message = CreateOrUpdateRealtimeModel(domain, modelId, colleciontId, objectValue, None, None, None, None)
    (modelClusterRegion ? message) map { _ => OkResponse }
  }
  
  def queryModels(authProfile: AuthorizationProfile, domain: DomainId, queryPost: ModelQueryPost): Future[RestResponse] = {
    val ModelQueryPost(query) = queryPost
    val userId = DomainUserId.convergence(authProfile.username)
    val message = DomainRestMessage(domain, QueryModelsRequest(userId, query))
    (domainRestActor ? message).mapTo[List[ModelQueryResult]].map(okResponse(_))
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
    notFoundResponse(Some(s"A model with id '${modelId}' does not exist."))
  }
}
