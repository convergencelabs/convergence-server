package com.convergencelabs.server.frontend.rest

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.ModelStoreActor.CreateModel
import com.convergencelabs.server.datastore.ModelStoreActor.CreateOrUpdateModel
import com.convergencelabs.server.datastore.ModelStoreActor.DeleteModel
import com.convergencelabs.server.datastore.ModelStoreActor.GetModel
import com.convergencelabs.server.datastore.ModelStoreActor.GetModels
import com.convergencelabs.server.datastore.ModelStoreActor.GetModelsInCollection
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.frontend.rest.DomainModelService.ModelMetaDataResponse
import com.convergencelabs.server.frontend.rest.DomainModelService.ModelResponse

import DomainModelService.CreateModelResponse
import DomainModelService.GetModelResponse
import DomainModelService.GetModelsResponse
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Directives.authorizeAsync
import akka.http.scaladsl.server.Route

import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.AuthorizationActor.ConvergenceAuthorizedRequest
import scala.util.Try
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.ModelPermissionsResponse
import com.convergencelabs.server.frontend.rest.DomainModelService.GetModelPermissionsResponse
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelWorldPermissions
import com.convergencelabs.server.frontend.rest.DomainModelService.GetPermissionsResponse
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.SetModelWorldPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.SetModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.RemoveModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelUserPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetAllModelUserPermissions
import com.convergencelabs.server.frontend.rest.DomainModelService.GetAllUserPermissionsResponse
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.GetModelOverridesPermissions
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.SetModelOverridesPermissions
import com.convergencelabs.server.frontend.rest.DomainModelService.GetModelOverridesPermissionsResponse
import com.convergencelabs.server.datastore.ModelPermissionsStoreActor.ModelUserPermissions

object DomainModelService {

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
  case class CreateModelResponse(collectionId: String, modelId: String) extends AbstractSuccessResponse
  case class GetModelPermissionsResponse(overrideWorld: Boolean, worldPermissions: ModelPermissions, userPermissions: List[ModelUserPermissions]) extends AbstractSuccessResponse
  case class GetPermissionsResponse(permissions: ModelPermissions) extends AbstractSuccessResponse
  case class GetAllUserPermissionsResponse(userPermissions: List[ModelUserPermissions]) extends AbstractSuccessResponse
  case class GetModelOverridesPermissionsResponse(overrideWorld: Boolean) extends AbstractSuccessResponse
}

class DomainModelService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authorizationActor: ActorRef,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("models") {
      authorizeAsync(canAccessDomain(domain, username)) {
        (pathEnd & get) {
          complete(getModels(domain))
        } ~ pathPrefix(Segment) { collectionId: String =>
          pathEnd {
            get {
              complete(getModelInCollection(domain, collectionId))
            } ~ post {
              entity(as[Map[String, Any]]) { data =>
                complete(postModel(domain, collectionId, data))
              }
            }
          } ~ pathPrefix(Segment) { modelId: String =>
            pathEnd {
              get {
                complete(getModel(domain, ModelFqn(collectionId, modelId)))
              } ~ put {
                entity(as[Map[String, Any]]) { data =>
                  complete(putModel(domain, collectionId, modelId, data))
                }
              } ~ delete {
                complete(deleteModel(domain, collectionId, modelId))
              }
            } ~ pathPrefix("permissions") {
              pathEnd {
                get {
                  complete(getModelPermissions(domain, ModelFqn(collectionId, modelId)))
                }
              } ~ pathPrefix("override") {
                pathEnd {
                  get {
                    complete(getModelOverridesPermissions(domain, ModelFqn(collectionId, modelId)))
                  } ~ put {
                    entity(as[Boolean]) { overridesPermissions =>
                      complete(setModelOverridesPermissions(domain, ModelFqn(collectionId, modelId), overridesPermissions))
                    }
                  }
                }
              } ~ pathPrefix("world") {
                pathEnd {
                  get {
                    complete(getModelWorldPermissions(domain, ModelFqn(collectionId, modelId)))
                  } ~ put {
                    entity(as[ModelPermissions]) { permissions =>
                      complete(setModelWorldPermissions(domain, ModelFqn(collectionId, modelId), permissions))
                    }
                  }
                } ~ pathPrefix("user") {
                  pathEnd {
                    get {
                      complete(getAllModelUserPermissions(domain, ModelFqn(collectionId, modelId)))
                    }
                  } ~ pathPrefix(Segment) { user: String =>
                    pathEnd {
                      get {
                        complete(getModelUserPermissions(domain, ModelFqn(collectionId, modelId), user))
                      } ~ put {
                        entity(as[ModelPermissions]) { permissions =>
                          complete(setModelUserPermissions(domain, ModelFqn(collectionId, modelId), user, permissions))
                        }
                      } ~ delete {
                        complete(removeModelUserPermissions(domain, ModelFqn(collectionId, modelId), user))
                      }
                    }
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
      metaData.fqn.collectionId,
      metaData.fqn.modelId,
      metaData.version,
      metaData.createdTime,
      metaData.modifiedTime)
  }

  def getModel(domain: DomainFqn, model: ModelFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModel(model))
    (domainRestActor ? message).mapTo[Option[Model]] map {
      case Some(model) =>
        val mr = ModelResponse(
          model.metaData.fqn.collectionId,
          model.metaData.fqn.modelId,
          model.metaData.version,
          model.metaData.createdTime,
          model.metaData.modifiedTime,
          model.data)
        (StatusCodes.OK, GetModelResponse(mr))
      case None =>
        NotFoundError
    }
  }

  //TODO: Pass in model permissions
  def postModel(domain: DomainFqn, colletionId: String, data: Map[String, Any]): Future[RestResponse] = {
    val message = DomainMessage(domain, CreateModel(colletionId, data, None, None))
    (domainRestActor ? message).mapTo[ModelFqn] map {
      case ModelFqn(collectionId, modelId) =>
        (StatusCodes.Created, CreateModelResponse(collectionId, modelId))
    }
  }

  //TODO: Pass in model permissions
  def putModel(domain: DomainFqn, colletionId: String, modelId: String, data: Map[String, Any]): Future[RestResponse] = {
    val message = DomainMessage(domain, CreateOrUpdateModel(colletionId, modelId, data, None, None))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def deleteModel(domain: DomainFqn, colletionId: String, modelId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, DeleteModel(ModelFqn(colletionId, modelId)))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  // Model Permissions

  def getModelOverridesPermissions(domain: DomainFqn, modelFqn: ModelFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModelOverridesPermissions(modelFqn))
    (domainRestActor ? message).mapTo[Boolean] map {
      overridesPermissions =>
        (StatusCodes.OK, GetModelOverridesPermissionsResponse(overridesPermissions))
    }
  }
  
  def setModelOverridesPermissions(domain: DomainFqn, modelFqn: ModelFqn, overridesPermissions: Boolean): Future[RestResponse] = {
    val message = DomainMessage(domain, SetModelOverridesPermissions(modelFqn, overridesPermissions))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getModelPermissions(domain: DomainFqn, modelFqn: ModelFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModelPermissions(modelFqn))
    (domainRestActor ? message).mapTo[ModelPermissionsResponse] map {
      response =>
        val ModelPermissionsResponse(overridePermissions, world, users) = response
        (StatusCodes.OK, GetModelPermissionsResponse(overridePermissions, world, users))
    }
  }

  def getModelWorldPermissions(domain: DomainFqn, modelFqn: ModelFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModelWorldPermissions(modelFqn))
    (domainRestActor ? message).mapTo[ModelPermissions] map {
      permissions =>
        (StatusCodes.OK, GetPermissionsResponse(permissions))
    }
  }

  def setModelWorldPermissions(domain: DomainFqn, modelFqn: ModelFqn, permissions: ModelPermissions): Future[RestResponse] = {
    val message = DomainMessage(domain, SetModelWorldPermissions(modelFqn, permissions))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def getAllModelUserPermissions(domain: DomainFqn, modelFqn: ModelFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetAllModelUserPermissions(modelFqn))
    (domainRestActor ? message).mapTo[List[ModelUserPermissions]] map {
      permissions =>
        (StatusCodes.OK, GetAllUserPermissionsResponse(permissions))
    }
  }

  def getModelUserPermissions(domain: DomainFqn, modelFqn: ModelFqn, username: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetModelUserPermissions(modelFqn, username))
    (domainRestActor ? message).mapTo[ModelPermissions] map {
      permissions =>
        (StatusCodes.OK, GetPermissionsResponse(permissions))
    }
  }

  def setModelUserPermissions(domain: DomainFqn, modelFqn: ModelFqn, username: String, permissions: ModelPermissions): Future[RestResponse] = {
    val message = DomainMessage(domain, SetModelUserPermissions(modelFqn, username, permissions))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def removeModelUserPermissions(domain: DomainFqn, modelFqn: ModelFqn, username: String): Future[RestResponse] = {
    val message = DomainMessage(domain, RemoveModelUserPermissions(modelFqn, username))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  // Permission Checks

  def canAccessDomain(domainFqn: DomainFqn, username: String): Future[Boolean] = {
    (authorizationActor ? ConvergenceAuthorizedRequest(username, domainFqn, Set("domain-access"))).mapTo[Try[Boolean]].map(_.get)
  }
}
