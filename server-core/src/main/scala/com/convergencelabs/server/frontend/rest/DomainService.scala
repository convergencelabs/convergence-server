package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.DeleteDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.GetDomainRequest
import com.convergencelabs.server.datastore.DomainStoreActor.ListDomainsRequest
import com.convergencelabs.server.datastore.DomainStoreActor.UpdateDomainRequest
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationDenied
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationFailure
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationGranted
import com.convergencelabs.server.domain.RestAuthnorizationActor.AuthorizationResult
import com.convergencelabs.server.domain.RestAuthnorizationActor.DomainAuthorization

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
import akka.http.scaladsl.server.directives.FutureDirectives.onSuccess
import akka.http.scaladsl.server.directives.OnSuccessMagnet.apply
import akka.pattern.ask
import akka.util.Timeout

case class DomainsResponse(domains: List[DomainInfo]) extends AbstractSuccessResponse
case class DomainResponse(domain: DomainInfo) extends AbstractSuccessResponse

case class DomainInfo(
  displayName: String,
  namespace: String,
  domainId: String,
  owner: String,
  status: String)

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

  val domainConfigService = new DomainConfigService(ec, domainManagerActor, t)
  val domainUserService = new DomainUserService(ec, domainManagerActor, t)
  val domainStatsService = new DomainStatsService(ec, domainManagerActor, t)
  val domainCollectionService = new DomainCollectionService(ec, domainManagerActor, t)
  val domainModelService = new DomainModelService(ec, domainManagerActor, t)
  val domainKeyService = new DomainKeyService(ec, domainManagerActor, t)
  val domainAdminTokenService = new DomainAdminTokenService(ec, domainManagerActor, t)

  val route = { username: String =>
    pathPrefix("domains") {
      pathEnd {
        get {
          complete(getDomains(username))
        } ~ post {
          entity(as[CreateDomainRestRequest]) { request =>
            complete(createDomain(request, username))
          }
        }
      } ~ pathPrefix(Segment / Segment) { (namespace, domainId) =>
        {
          val domain = DomainFqn(namespace, domainId)
          onSuccess((authz ? DomainAuthorization(username, domain)).mapTo[AuthorizationResult]) {
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
                domainUserService.route(username, domain) ~
                domainCollectionService.route(username, domain) ~
                domainModelService.route(username, domain) ~
                domainKeyService.route(username, domain) ~
                domainAdminTokenService.route(username, domain) ~
                domainConfigService.route(username, domain) ~
                domainStatsService.route(username, domain)
            case AuthorizationDenied =>
              complete(ForbiddenError)
            case AuthorizationFailure =>
              complete(InternalServerError)
          }
        }
      }
    }
  }

  def createDomain(createRequest: CreateDomainRestRequest, username: String): Future[RestResponse] = {
    val CreateDomainRestRequest(namespace, domainId, displayName) = createRequest
    val message = CreateDomainRequest(namespace, domainId, displayName, username)
    (domainStoreActor ? message).mapTo[Unit].map { _ => CreateRestResponse }
  }

  def getDomains(username: String): Future[RestResponse] = {
    (domainStoreActor ? ListDomainsRequest(username)).mapTo[List[Domain]].map(domains =>
      (StatusCodes.OK, DomainsResponse(
        (domains map (domain => DomainInfo(
          domain.displayName,
          domain.domainFqn.namespace,
          domain.domainFqn.domainId,
          domain.owner,
          domain.status.toString()))))))
  }

  def getDomain(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? GetDomainRequest(namespace, domainId)).mapTo[Option[Domain]].map {
      case Some(domain) =>
        (StatusCodes.OK, DomainResponse(DomainInfo(
          domain.displayName,
          domain.domainFqn.namespace,
          domain.domainFqn.domainId,
          domain.owner,
          domain.status.toString())))
      case None => 
        NotFoundError
    }
  }

  def deleteDomain(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? DeleteDomainRequest(namespace, domainId)) map { _ => OkResponse }
  }

  def updateDomain(namespace: String, domainId: String, request: UpdateDomainRestRequest): Future[RestResponse] = {
    val UpdateDomainRestRequest(displayName) = request
    val message = UpdateDomainRequest(namespace, domainId, displayName)
    (domainStoreActor ? message) map { _ => OkResponse }
  }
}
