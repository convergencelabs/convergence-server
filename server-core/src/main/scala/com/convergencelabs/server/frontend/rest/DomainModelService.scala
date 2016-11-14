package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.ModelStoreActor.GetModel
import com.convergencelabs.server.datastore.ModelStoreActor.GetModels
import com.convergencelabs.server.datastore.ModelStoreActor.GetModelsInCollection
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.server.Route
import DomainModelService.GetModelsResponse
import DomainModelService.GetModelResponse
import DomainModelService.CreateModelResponse
import com.convergencelabs.server.datastore.ModelStoreActor.CreateOrUpdateModel
import com.convergencelabs.server.datastore.ModelStoreActor.DeleteModel
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.UpdateSuccess
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.CreateOrUpdateResult
import com.convergencelabs.server.datastore.ModelStoreActor.CreateModel
import com.convergencelabs.server.datastore.DuplicateValue

object DomainModelService {
  case class GetModelsResponse(models: List[Model]) extends AbstractSuccessResponse
  case class GetModelResponse(model: Model) extends AbstractSuccessResponse
  case class CreateModelResponse(collectionId: String, modelId: String) extends AbstractSuccessResponse
}

class DomainModelService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("models") {
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
          }
        }
      }
    }
  }

  def getModels(domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(
      domain,
      GetModels(None, None))).mapTo[List[Model]] map
      (models => (StatusCodes.OK, GetModelsResponse(models)))
  }

  def getModelInCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(
      domain,
      GetModelsInCollection(collectionId, None, None))).mapTo[List[Model]] map
      (models => (StatusCodes.OK, GetModelsResponse(models)))
  }

  def getModel(domain: DomainFqn, model: ModelFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(
      domain,
      GetModel(model))).mapTo[Option[Model]] map {
        case Some(model) => (StatusCodes.OK, GetModelResponse(model))
        case None => NotFoundError
      }
  }
  
  def postModel(domain: DomainFqn, colletionId: String, data: Map[String, Any]): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(
      domain,
      CreateModel(colletionId, data))).mapTo[CreateResult[ModelFqn]] map {
        case CreateSuccess(ModelFqn(collectionId, modelId)) =>
          (StatusCodes.OK, CreateModelResponse(collectionId, modelId)) 
        case InvalidValue => InvalidValueError
        case DuplicateValue => DuplicateError
      }
  }
  
  def putModel(domain: DomainFqn, colletionId: String, modelId: String, data: Map[String, Any]): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(
      domain,
      CreateOrUpdateModel(colletionId, modelId, data))).mapTo[CreateOrUpdateResult[Unit]] map {
        case CreateSuccess(()) => OkResponse 
        case UpdateSuccess => OkResponse
        case InvalidValue => InvalidValueError
      }
  }
  
  def deleteModel(domain: DomainFqn, colletionId: String, modelId: String): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(
      domain,
      DeleteModel(ModelFqn(colletionId, modelId)))).mapTo[DeleteResult] map {
        case DeleteSuccess => OkResponse
        case NotFound => NotFoundError
      }
  }
}
