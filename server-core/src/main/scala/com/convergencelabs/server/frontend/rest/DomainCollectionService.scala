package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.CollectionStoreActor.CollectionInfo
import com.convergencelabs.server.datastore.CollectionStoreActor.GetCollectionsRequest
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestDomainActor.DomainMessage

import DomainCollectionService.GetCollectionsResponse
import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.segmentStringToPathMatcher
import akka.pattern.ask
import akka.util.Timeout

object DomainCollectionService {
  case class GetCollectionsResponse(ok: Boolean, collections: List[CollectionInfo]) extends ResponseMessage
}

class DomainCollectionService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  import DomainCollectionService._

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(userId: String, namespace: String, domainId: String) = {
    pathPrefix("collections") {
      get {
        complete(getCollectionsRequest(namespace, domainId))
      }
    }
  }

  def getCollectionsRequest(namespace: String, domainId: String): Future[RestResponse] = {
    (domainRestActor ? DomainMessage(
      DomainFqn(namespace, domainId),
      GetCollectionsRequest(None, None))).mapTo[List[CollectionInfo]] map
      (collections => (StatusCodes.OK, GetCollectionsResponse(true, collections)))
  }
}
