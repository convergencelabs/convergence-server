package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.CollectionStoreActor.CreateCollection
import com.convergencelabs.server.datastore.CollectionStoreActor.UpdateCollection
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
import akka.http.scaladsl.server.Directives.authorizeAsync
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.AuthorizationActor.ConvergenceAuthorizedRequest
import scala.util.Try

object DomainCollectionService {
  case class GetCollectionsResponse(collections: List[Collection]) extends AbstractSuccessResponse
  case class GetCollectionResponse(collection: Collection) extends AbstractSuccessResponse
}

class DomainCollectionService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authorizationActor: ActorRef,
  private[this] val domainRestActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  def route(username: String, domain: DomainFqn): Route = {
    pathPrefix("collections") {
      pathEnd {
        get {
          authorizeAsync(canAccessDomain(domain, username)) {
            complete(getCollections(domain))
          }
        } ~ post {
          entity(as[Collection]) { collection =>
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(createCollection(domain, collection))
            }
          }
        }
      } ~ pathPrefix(Segment) { collectionId =>
        pathEnd {
          get {
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(getCollection(domain, collectionId))
            }
          } ~ delete {
            authorizeAsync(canAccessDomain(domain, username)) {
              complete(deleteCollection(domain, collectionId))
            }
          }  ~ put {
            entity(as[Collection]) { updateData =>
              authorizeAsync(canAccessDomain(domain, username)) {
                complete(updateCollection(domain, collectionId, updateData))
              }
            }
          }
        }
      }
    }
  }

  def getCollections(domain: DomainFqn): Future[RestResponse] = {
    val message = DomainMessage(domain, GetCollections(None, None))
    (domainRestActor ? message).mapTo[List[Collection]] map
      (collections => (StatusCodes.OK, GetCollectionsResponse(collections)))
  }

  def getCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, GetCollection(collectionId))
    (domainRestActor ? message).mapTo[Option[Collection]] map {
      case Some(collection) => (StatusCodes.OK, GetCollectionResponse(collection))
      case None             => NotFoundError
    }
  }

  def createCollection(domain: DomainFqn, collection: Collection): Future[RestResponse] = {
    val message = DomainMessage(domain, CreateCollection(collection))
    (domainRestActor ? message) map { _ => CreateRestResponse }
  }
  
  def updateCollection(domain: DomainFqn, collectionId: String, collection: Collection): Future[RestResponse] = {
    val message = DomainMessage(domain, UpdateCollection(collectionId, collection))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  def deleteCollection(domain: DomainFqn, collectionId: String): Future[RestResponse] = {
    val message = DomainMessage(domain, DeleteCollection(collectionId))
    (domainRestActor ? message) map { _ => OkResponse }
  }

  // Permission Checks

  def canAccessDomain(domainFqn: DomainFqn, username: String): Future[Boolean] = {
    (authorizationActor ? ConvergenceAuthorizedRequest(username, domainFqn, Set("domain-access"))).mapTo[Try[Boolean]].map(_.get)
  }
}
