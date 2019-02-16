package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.DomainStoreActor.CreateDomainRequest
import com.convergencelabs.server.datastore.convergence.DomainStoreActor.DeleteDomainRequest
import com.convergencelabs.server.datastore.convergence.DomainStoreActor.GetDomainRequest
import com.convergencelabs.server.datastore.convergence.DomainStoreActor.ListDomainsRequest
import com.convergencelabs.server.datastore.convergence.DomainStoreActor.UpdateDomainRequest
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.frontend.rest.domain.DomainAdminTokenService
import com.convergencelabs.server.frontend.rest.domain.DomainStatsService
import com.convergencelabs.server.security.AuthorizationProfile

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives._string2NR
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.authorize
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.parameters
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.util.Timeout
import com.convergencelabs.server.datastore.convergence.NamespaceNotFoundException

object DomainService {
  case class CreateDomainRestRequest(namespace: String, id: String, displayName: String)
  case class UpdateDomainRestRequest(displayName: String)
}

class DomainService(
  private[this] val executionContext: ExecutionContext,
  private[this] val domainStoreActor: ActorRef,
  private[this] val domainManagerActor: ActorRef, // RestDomainActor
  private[this] val permissionStoreActor: ActorRef,
  private[this] val modelClusterRegion: ActorRef,
  private[this] val defaultTimeout: Timeout)
  extends JsonSupport {

  import DomainService._
  import akka.http.scaladsl.server.Directives.Segment
  import akka.pattern.ask

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val domainConfigService = new DomainConfigService(ec, t, domainManagerActor)
  val domainUserService = new DomainUserService(ec, t, domainManagerActor)
  val domainUserGroupService = new DomainUserGroupService(ec, t, domainManagerActor)
  val domainStatsService = new DomainStatsService(ec, t, domainManagerActor)
  val domainCollectionService = new DomainCollectionService(ec, t, domainManagerActor)
  val domainSessionService = new DomainSessionService(ec, t, domainManagerActor)
  val domainModelService = new DomainModelService(ec, t, domainManagerActor, modelClusterRegion)
  val domainKeyService = new DomainKeyService(ec, t, domainManagerActor)
  val domainAdminTokenService = new DomainAdminTokenService(ec, t, domainManagerActor)
  val domainSecurityService = new DomainSecurityService(ec, t, permissionStoreActor)

  val route = { authProfile: AuthorizationProfile =>
    pathPrefix("domains") {
      pathEnd {
        get {
          parameters("namespace".?, "filter".?, "offset".as[Int].?, "limit".as[Int].?) { (namespace, filter, offset, limit) =>
            complete(getDomains(authProfile, namespace, filter, offset, limit))
          }
        } ~ post {
          entity(as[CreateDomainRestRequest]) { request =>
            complete(createDomain(request, authProfile))
          }
        }
      } ~ pathPrefix(Segment / Segment) { (namespace, domainId) =>
        {
          val domain = DomainFqn(namespace, domainId)
          pathEnd {
            get {
              authorize(canAccessDomain(domain, authProfile)) {
                complete(getDomain(namespace, domainId))
              }
            } ~ delete {
              authorize(canAccessDomain(domain, authProfile)) {
                complete(deleteDomain(namespace, domainId))
              }
            } ~ put {
              entity(as[UpdateDomainRestRequest]) { request =>
                authorize(canAccessDomain(domain, authProfile)) {
                  complete(updateDomain(namespace, domainId, request))
                }
              }
            }
          } ~
            domainUserService.route(authProfile, domain) ~
            domainCollectionService.route(authProfile, domain) ~
            domainModelService.route(authProfile, domain) ~
            domainKeyService.route(authProfile, domain) ~
            domainAdminTokenService.route(authProfile, domain) ~
            domainConfigService.route(authProfile, domain) ~
            domainStatsService.route(authProfile, domain) ~
            domainSecurityService.route(authProfile, domain) ~
            domainSessionService.route(authProfile, domain) ~
            domainUserGroupService.route(authProfile, domain)
        }
      }
    }
  }

  def createDomain(createRequest: CreateDomainRestRequest, authProfile: AuthorizationProfile): Future[RestResponse] = {
    val CreateDomainRestRequest(namespace, id, displayName) = createRequest
    // FIXME require permissions
    // FIXME check config to see if user namespaces are enabled
    val message = CreateDomainRequest(namespace, id, displayName, false)
    (domainStoreActor ? message)
      .map { _ => CreatedResponse }
      .recover {
        case NamespaceNotFoundException(namespace) =>
          namespaceNotFoundResponse(namespace)
      }
  }

  def getDomains(authProfile: AuthorizationProfile, namespace: Option[String], filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    (domainStoreActor ? ListDomainsRequest(authProfile, namespace, filter, offset, limit)).mapTo[List[Domain]].map(domains =>
      okResponse(
        domains map (domain => DomainRestData(
          domain.displayName,
          domain.domainFqn.namespace,
          domain.domainFqn.domainId,
          domain.status.toString))))
  }

  def getDomain(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? GetDomainRequest(namespace, domainId)).mapTo[Option[Domain]].map {
      case Some(domain) =>
        okResponse(DomainRestData(
          domain.displayName,
          domain.domainFqn.namespace,
          domain.domainFqn.domainId,
          domain.status.toString()))
      case None =>
        notFoundResponse()
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

  // Permission Checks
  def canAccessDomain(domainFqn: DomainFqn, authProfile: AuthorizationProfile): Boolean = {
    // FIXME clearly not correct
    true
  }
}
