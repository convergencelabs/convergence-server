package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.CollectionStoreActor.CollectionInfo
import com.convergencelabs.server.datastore.CollectionStoreActor.GetCollection
import com.convergencelabs.server.datastore.CollectionStoreActor.GetCollections
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainActor.DomainMessage
import com.convergencelabs.server.domain.model.Collection

import DomainCollectionService.GetCollectionResponse
import DomainCollectionService.GetCollectionsResponse
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.segmentStringToPathMatcher
import akka.pattern.ask
import akka.util.Timeout
import akka.http.scaladsl.server.Route

object DomainCollectionService {
  case class GetCollectionsResponse(collections: List[CollectionInfo]) extends AbstractSuccessResponse
  case class GetCollectionResponse(collection: Collection) extends AbstractSuccessResponse
}

class DomainCollectionService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(userId: String, domain: DomainFqn): Route = {
    pathPrefix("collections") {
      pathEnd {
        get {
          complete(getCollections(domain))
        }
      } ~ pathPrefix(Segment) { collectionId =>
        pathEnd {
          get {
            complete(getCollection(domain, collectionId))
          }
        }
      }
    }
  }

  def getCollections(domain: DomainFqn): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(
      domain,
      GetCollections(None, None))).mapTo[List[CollectionInfo]] map
      (collections => (StatusCodes.OK, GetCollectionsResponse(collections)))
  }

  def getCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(domain, GetCollection(collectionId))).mapTo[Option[Collection]] map {
      case Some(collection) => (StatusCodes.OK, GetCollectionResponse(collection))
      case None => (StatusCodes.NotFound, ErrorResponse("collection_not_found"))
    }
  }
}
