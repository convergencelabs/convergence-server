package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.UpdateDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.DeleteDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.ListDomainsRequest
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._
import akka.pattern._
import akka.util.Timeout
import com.convergencelabs.server.datastore.CreateResult
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.DeleteResult
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.NotFound
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.domain.RestAuthnorizationActor.DomainAuthorization
import scala.util.Failure
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationGranted
import scala.util.Success
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationDenied
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationResult
import com.convergencelabs.server.datastore.UpdateResult
import com.convergencelabs.server.datastore.UpdateSuccess

case class DomainsResponse(domains: List[DomainFqn]) extends AbstractSuccessResponse
case class DomainResponse(domain: DomainInfo) extends AbstractSuccessResponse

case class DomainInfo(
  id: String,
  displayName: String,
  namespace: String,
  domainId: String,
  owner: String)

case class CreateDomainRestRequest(namespace: String, domainId: String, displayName: String)
case class UpdateDomainRestRequest(displayName: String)

class DomainService(
  private[this] val executionContext: ExecutionContext,
  private[this] val authz: ActorRef,
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
          complete(getDomains(userId))
        } ~ post {
          entity(as[CreateDomainRestRequest]) { request =>
            complete(createDomain(request, userId))
          }
        }
      } ~ pathPrefix(Segment / Segment) { (namespace, domainId) =>
        {
          val domain = DomainFqn(namespace, domainId)
          onSuccess((authz ? DomainAuthorization(userId, domain)).mapTo[AuthorizationResult]) {
            case AuthorizationGranted =>
              pathEnd {
                get {
                  complete(getDomain(namespace, domainId))
                } ~ delete {
                  complete(deleteDomain(namespace, domainId))
                } ~ put {
                  entity(as[UpdateDomainRestRequest]) { request =>
                    complete(updateDomain(namespace, domainId, request))
                  }
                }
              } ~
                domainUserService.route(userId, domain) ~
                domainCollectionService.route(userId, domain) ~
                domainModelService.route(userId, domain) ~
                domainKeyService.route(userId, domain) ~
                domainAdminTokenService.route(userId, domain)
            case AuthorizationDenied =>
              complete(ForbiddenError)
          }
        }
      }
    }
  }

  def createDomain(createRequest: CreateDomainRestRequest, userId: String): Future[RestResponse] = {
    val CreateDomainRestRequest(namespace, domainId, displayName) = createRequest
    (domainStoreActor ? CreateDomainRequest(namespace, domainId, displayName, userId, None)).mapTo[CreateResult[Unit]].map {
      case result: CreateSuccess[Unit] => CreateRestResponse
      case DuplicateValue => DuplicateError
      case InvalidValue => InvalidValueError
    }
  }

  def getDomains(userId: String): Future[RestResponse] = {
    (domainStoreActor ? ListDomainsRequest(userId)).mapTo[List[Domain]].map(domains =>
      (StatusCodes.OK, DomainsResponse(
        (domains map (domain => DomainFqn(domain.domainFqn.namespace, domain.domainFqn.domainId))))))

  }

  def getDomain(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? GetDomainRequest(namespace, domainId)).mapTo[Option[Domain]].map {
      case Some(domain) =>
        (StatusCodes.OK, DomainResponse(DomainInfo(
          domain.id,
          domain.displayName,
          domain.domainFqn.namespace,
          domain.domainFqn.domainId,
          domain.owner)))
      case None => NotFoundError
    }
  }

  def deleteDomain(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? DeleteDomainRequest(namespace, domainId)).mapTo[DeleteResult] map {
      case DeleteSuccess => OkResponse
      case NotFound => NotFoundError
    }
  }

  def updateDomain(namespace: String, domainId: String, request: UpdateDomainRestRequest): Future[RestResponse] = {
    val UpdateDomainRestRequest(displayName) = request
    (domainStoreActor ? UpdateDomainRequest(namespace, domainId, displayName)).mapTo[UpdateResult].map {
      case UpdateSuccess => OkResponse
      case InvalidValue => InvalidValueError
      case NotFound => NotFoundError
    }
  }
}
