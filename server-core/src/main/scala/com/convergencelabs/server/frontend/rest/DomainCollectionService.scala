package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.CollectionStoreActor.CreateCollection
import com.convergencelabs.server.datastore.CollectionStoreActor.DeleteCollection
import com.convergencelabs.server.datastore.CollectionStoreActor.GetCollection
import com.convergencelabs.server.datastore.CollectionStoreActor.GetCollections
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainManagerActor.DomainMessage
import com.convergencelabs.server.domain.model.Collection

import DomainCollectionService.GetCollectionResponse
import DomainCollectionService.GetCollectionsResponse
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout

object DomainCollectionService {
  case class GetCollectionsResponse(collections: List[Collection]) extends AbstractSuccessResponse
  case class GetCollectionResponse(collection: Collection) extends AbstractSuccessResponse
}

class DomainCollectionService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("collections") {
      pathEnd {
        get {
          complete(getCollections(domain))
        } ~ post {
          entity(as[Collection]) { collection =>
            complete(createCollection(domain, collection))
          }
        }
      } ~ pathPrefix(Segment) { collectionId =>
        pathEnd {
          get {
            complete(getCollection(domain, collectionId))
          } ~ delete {
            complete(deleteCollection(domain, collectionId))
          }
        }
      }
    }
  }

  def getCollections(domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(
      domain,
      GetCollections(None, None))).mapTo[List[Collection]] map
      (collections => (StatusCodes.OK, GetCollectionsResponse(collections)))
  }

  def getCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetCollection(collectionId))).mapTo[Option[Collection]] map {
      case Some(collection) => (StatusCodes.OK, GetCollectionResponse(collection))
      case None             => NotFoundError
    }
  }

  def createCollection(domain: DomainFqn, collection: Collection): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, CreateCollection(collection))) map { _ =>
      CreateRestResponse
    }
  }

  def deleteCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, DeleteCollection(collectionId))) map {_ =>
      OkResponse
    }
  }
}
