package com.convergencelabs.server.frontend.rest

import java.time.Instant
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetAllModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelOverridesPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelWorldPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.ModelPermissionsResponse
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.ModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.RemoveModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.SetModelOverridesPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.SetModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.SetModelWorldPermissions
import com.convergencelabs.server.datastore.ModelStoreActor.GetModels
import com.convergencelabs.server.datastore.ModelStoreActor.GetModelsInCollection
import com.convergencelabs.server.datastore.domain.ModelDataGenerator
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.AuthorizationActor.ConvergenceAuthorizedRequest
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage
import com.convergencelabs.server.domain.model.CreateOrUpdateRealtimeModel
import com.convergencelabs.server.domain.model.CreateRealtimeModel
import com.convergencelabs.server.domain.model.DeleteRealtimeModel
import com.convergencelabs.server.domain.model.GetRealtimeModel
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.frontend.rest.DomainModelService.GetAllUserPermissionsResponse
import com.convergencelabs.server.frontend.rest.DomainModelService.GetModelOverridesPermissionsResponse
import com.convergencelabs.server.frontend.rest.DomainModelService.GetModelPermissionsResponse
import com.convergencelabs.server.frontend.rest.DomainModelService.GetPermissionsResponse
import com.convergencelabs.server.frontend.rest.DomainModelService.ModelMetaDataResponse
import com.convergencelabs.server.frontend.rest.DomainModelService.ModelPermissionsSummary
import com.convergencelabs.server.frontend.rest.DomainModelService.ModelResponse
import com.convergencelabs.server.frontend.rest.DomainModelService.SetOverrideWorldRequest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.authorizeAsync
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import akka.pattern.ask

object DomainModelService {

  case class ModelPost(
    collection: String,
    data: Map[String, Any])

  case class ModelPut(
    collection: String,
    data: Map[String, Any])

  case class ModelMetaDataResponse(
    collectionId: String,
    modelId: String,
    version: Long,
    createdTime: Instant,
    modifiedTime: Instant)

  case class ModelResponse(
    collectionId: String,
    modelId: String,
    version: Long,
    createdTime: Instant,
    modifiedTime: Instant,
    data: ObjectValue)

  case class GetModelsResponse(models: List[ModelMetaDataResponse]) extends AbstractSuccessResponse
  case class GetModelResponse(model: ModelResponse) extends AbstractSuccessResponse
  case class CreateModelResponse(modelId: String) extends AbstractSuccessResponse
  case class GetModelPermissionsResponse(permissions: ModelPermissionsSummary) extends AbstractSuccessResponse
  case class ModelPermissionsSummary(overrideWorld: Boolean, worldPermissions: ModelPermissions, userPermissions: List[ModelUserPermissions])
  case class GetPermissionsResponse(permissions: Option[ModelPermissions]) extends AbstractSuccessResponse
  case class GetAllUserPermissionsResponse(userPermissions: List[ModelUserPermissions]) extends AbstractSuccessResponse
  case class GetModelOverridesPermissionsResponse(overrideWorld: Boolean) extends AbstractSuccessResponse
  case class SetOverrideWorldRequest(overrideWorld: Boolean)
}

class DomainModelService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authorizationActor: ActorRef,
  private[this] val domainRestActor: ActorRef,
  private[this] val modelClusterRegion: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {
  
  import DomainModelService._

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("models") {
      authorizeAsync(canAccessDomain(domain, username)) {
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
      }
    }
  }

  def getModels(domain: DomainFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModels(None, None))
    (domainRestActor ? message).mapTo[List[ModelMetaData]] map {
      _.map(mapMetaData(_))
    } map {
      models => (StatusCodes.OK, GetModelsResponse(models))
    }
  }

  def getModelInCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModelsInCollection(collectionId, None, None))
    (domainRestActor ? message).mapTo[List[ModelMetaData]] map {
      _.map(mapMetaData(_))
    } map {
      models => (StatusCodes.OK, GetModelsResponse(models))
    }
  }

  def mapMetaData(metaData: ModelMetaData): ModelMetaDataResponse = {
    ModelMetaDataResponse(
      metaData.collectionId,
      metaData.modelId,
      metaData.version,
      metaData.createdTime,
      metaData.modifiedTime)
  }

  def getModel(domain: DomainFqn, modelId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetRealtimeModel(domain, modelId, None))
    (domainRestActor ? message).mapTo[Option[Model]] map {
      case Some(model) =>
        val mr = ModelResponse(
          model.metaData.collectionId,
          model.metaData.modelId,
          model.metaData.version,
          model.metaData.createdTime,
          model.metaData.modifiedTime,
          model.data)
        (StatusCodes.OK, GetModelResponse(mr))
      case None =>
        NotFoundError
    }
  }

  def postModel(domain: DomainFqn, model: ModelPost): Future[RestResponse] = {
    val ModelPost(colletionId, data) = model

    // FIXME abstract this.
    val modelId = UUID.randomUUID().toString()
    val objectValue = ModelDataGenerator(data)
    // FIXME need to pass in model permissions options.
    val message = CreateRealtimeModel(domain, modelId, colletionId, objectValue, None, None, None, None)
    (modelClusterRegion ? message).mapTo[String] map {
      case modelId: String =>
        (StatusCodes.Created, CreateModelResponse(modelId))
    }
  }

  def putModel(domain: DomainFqn, modelId: String, modelPut: ModelPut): Future[RestResponse] = {
    val ModelPut(colleciontId, data) = modelPut
    val objectValue = ModelDataGenerator(data)
    // FIXME need to pass id model permissions options.
    val message = CreateOrUpdateRealtimeModel(domain, modelId, colleciontId, objectValue, None, None, None, None)
    (modelClusterRegion ? message) map { _ => OkResponse }
  }

  def deleteModel(domain: DomainFqn, modelId: String): Future[RestResponse] = {
    val message = DeleteRealtimeModel(domain, modelId, None)
    (modelClusterRegion ? message) map { _ => OkResponse }
  }

  // Model Permissions

  def getModelOverridesPermissions(domain: DomainFqn, modelId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModelOverridesPermissions(modelId))
    (domainRestActor ? message).mapTo[Boolean] map {
      overridesPermissions =>
        (StatusCodes.OK, GetModelOverridesPermissionsResponse(overridesPermissions))
    }
  }

  def setModelOverridesPermissions(domain: DomainFqn, modelId: String, overridesPermissions: SetOverrideWorldRequest): Future[RestResponse] = {
    val SetOverrideWorldRequest(overrideWorld) = overridesPermissions
    val message = DomainMessage(domain, SetModelOverridesPermissions(modelId, overrideWorld))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getModelPermissions(domain: DomainFqn, modelId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModelPermissions(modelId))
    (domainRestActor ? message).mapTo[ModelPermissionsResponse] map {
      response =>
        val ModelPermissionsResponse(overridePermissions, world, users) = response
        (StatusCodes.OK, GetModelPermissionsResponse(ModelPermissionsSummary(overridePermissions, world, users)))
    }
  }

  def getModelWorldPermissions(domain: DomainFqn, modelId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModelWorldPermissions(modelId))
    (domainRestActor ? message).mapTo[ModelPermissions] map {
      permissions =>
        (StatusCodes.OK, GetPermissionsResponse(Some(permissions)))
    }
  }

  def setModelWorldPermissions(domain: DomainFqn, modelId: String, permissions: ModelPermissions): Future[RestResponse] = {
    val message = DomainMessage(domain, SetModelWorldPermissions(modelId, permissions))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getAllModelUserPermissions(domain: DomainFqn, modelId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetAllModelUserPermissions(modelId))
    (domainRestActor ? message).mapTo[List[ModelUserPermissions]] map {
      permissions =>
        (StatusCodes.OK, GetAllUserPermissionsResponse(permissions))
    }
  }

  def getModelUserPermissions(domain: DomainFqn, modelId: String, username: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModelUserPermissions(modelId, username))
    (domainRestActor ? message).mapTo[Option[ModelPermissions]] map {
      permissions =>
        (StatusCodes.OK, GetPermissionsResponse(permissions))
    }
  }

  def setModelUserPermissions(domain: DomainFqn, modelId: String, username: String, permissions: ModelPermissions): Future[RestResponse] = {
    val message = DomainMessage(domain, SetModelUserPermissions(modelId, username, permissions))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def removeModelUserPermissions(domain: DomainFqn, modelId: String, username: String): Future[RestResponse] = {
    val message = DomainMessage(domain, RemoveModelUserPermissions(modelId, username))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  // Permission Checks

  def canAccessDomain(domainFqn: DomainFqn, username: String): Future[Boolean] = {
    (authorizationActor ? ConvergenceAuthorizedRequest(username, domainFqn, Set("domain-access"))).mapTo[Try[Boolean]].map(_.get)
  }
}
