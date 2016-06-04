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
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.server.Route
import DomainModelService.GetModelsResponse
import DomainModelService.GetModelResponse

object DomainModelService {
  case class GetModelsResponse(models: List[Model]) extends AbstractSuccessResponse
  case class GetModelResponse(model: Model) extends AbstractSuccessResponse
}

class DomainModelService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(userId: String, domain: DomainFqn): Route = {
    pathPrefix("models") {
      (pathEnd & get) {
        complete(getModels(domain))
      } ~ pathPrefix(Segment) { collectionId: String =>
        pathEnd {
          get {
            complete(getModelInCollection(domain, collectionId))
          }
        } ~ pathPrefix(Segment) { modelId: String =>
          pathEnd {
            get {
              complete(getModel(domain, ModelFqn(collectionId, modelId)))
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
}
