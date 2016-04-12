package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.DeleteDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.DomainNotFound
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainResponse
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainSuccess
import com.convergencelabs.server.datastore.DomainStoreActor.ListDomainsRequest
import com.convergencelabs.server.datastore.DomainStoreActor.ListDomainsResponse
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.segmentStringToPathMatcher
import akka.pattern.ask
import akka.util.Timeout

case class DomainsResponse(domains: List[DomainFqn]) extends AbstractSuccessResponse
case class DomainResponse(domain: DomainInfo) extends AbstractSuccessResponse
case class CreateResponse() extends AbstractSuccessResponse
case class DeleteResponse() extends AbstractSuccessResponse

case class DomainInfo(
  id: String,
  displayName: String,
  namespace: String,
  domainId: String,
  owner: String)

case class CreateRequest(namespace: String, domainId: String, displayName: String)

class DomainService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainStoreActor: ActorRef,
  private[this] val domainManagerActor: ActorRef,
  private[this] val defaultTimeout: Timeout)
    extends JsonSupport {

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val domainUserService = new DomainUserService(ec, domainManagerActor, t)
  val domainCollectionService = new DomainCollectionService(ec, domainManagerActor, t)
  val domainModelService = new DomainModelService(ec, domainManagerActor, t)
  val domainKeyService = new DomainKeyService(ec, domainManagerActor, t)
  val domainAdminTokenService = new DomainAdminTokenService(ec, domainManagerActor, t)

  val route = { userId: String =>
    pathPrefix("domains") {
      pathEnd {
        get {
          complete(domainsRequest(userId))
        } ~ post {
          entity(as[CreateRequest]) { request =>
            complete(createRequest(request, userId))
          }
        }
      } ~ pathPrefix(Segment / Segment) { (namespace, domainId) =>
        {
          val domain = DomainFqn(namespace, domainId)
          pathEnd {
            get {
              complete(domainRequest(namespace, domainId))
            } ~ delete {
              complete(deleteRequest(namespace, domainId))
            }
          } ~
            domainUserService.route(userId, domain) ~
            domainCollectionService.route(userId, domain) ~
            domainModelService.route(userId, domain)~
            domainKeyService.route(userId, domain) ~
            domainAdminTokenService.route(userId, domain)
        }
      }
    }
  }

  def createRequest(createRequest: CreateRequest, userId: String): Future[RestResponse] = {
    val CreateRequest(namespace, domainId, displayName) = createRequest
    (domainStoreActor ? CreateDomainRequest(namespace, domainId, displayName, userId)).mapTo[Unit].map {
      case _ => (StatusCodes.Created, CreateResponse())
    }
  }

  def domainsRequest(userId: String): Future[RestResponse] = {
    (domainStoreActor ? ListDomainsRequest(userId)).mapTo[ListDomainsResponse].map {
      case ListDomainsResponse(domains) => (StatusCodes.OK, DomainsResponse(
        (domains map (domain => DomainFqn(domain.domainFqn.namespace, domain.domainFqn.domainId)))))
    }
  }

  def domainRequest(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? GetDomainRequest(namespace, domainId)).mapTo[GetDomainResponse].map {
      case GetDomainSuccess(domain) =>
        (StatusCodes.OK, DomainResponse(DomainInfo(
          domain.id,
          domain.displayName,
          domain.domainFqn.namespace,
          domain.domainFqn.domainId,
          domain.owner)))
      case DomainNotFound =>
        (StatusCodes.NotFound, ErrorResponse("Domain not found!"))
    }
  }

  def deleteRequest(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? DeleteDomainRequest(namespace, domainId)) map {
      case _ => (StatusCodes.Created, DeleteResponse())
    }
  }
}
