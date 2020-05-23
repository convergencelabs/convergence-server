/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.rest.domain

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, _string2NR, as, authorize, complete, delete, entity, get, parameters, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.datastore.convergence.DomainStoreActor._
import com.convergencelabs.convergence.server.datastore.convergence.NamespaceNotFoundException
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

object DomainService {

  case class CreateDomainRestRequest(namespace: String, id: String, displayName: String)

  case class UpdateDomainRestRequest(displayName: String)

}

class DomainService(private[this] val executionContext: ExecutionContext,
                    private[this] val domainStoreActor: ActorRef,
                    private[this] val domainManagerActor: ActorRef, // RestDomainActor
                    private[this] val permissionStoreActor: ActorRef,
                    private[this] val modelClusterRegion: ActorRef,
                    private[this] val chatClusterRegion: ActorRef,
                    private[this] val defaultTimeout: Timeout)
  extends AbstractDomainRestService(executionContext, defaultTimeout) {

  import DomainService._

  val domainConfigService = new DomainConfigService(ec, t, domainManagerActor)
  val domainUserService = new DomainUserService(ec, t, domainManagerActor)
  val domainUserGroupService = new DomainUserGroupService(ec, t, domainManagerActor)
  val domainStatsService = new DomainStatsService(ec, t, domainManagerActor)
  val domainCollectionService = new DomainCollectionService(ec, t, domainManagerActor)
  val domainSessionService = new DomainSessionService(ec, t, domainManagerActor)
  val domainModelService = new DomainModelService(ec, t, domainManagerActor, modelClusterRegion)
  val domainKeyService = new DomainKeyService(ec, t, domainManagerActor)
  val domainAdminTokenService = new DomainAdminTokenService(ec, t, domainManagerActor)
  val domainChatService = new DomainChatService(ec, t, domainManagerActor, chatClusterRegion)
  val domainSecurityService = new DomainMembersService(ec, t, permissionStoreActor)

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
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
        val domain = DomainId(namespace, domainId)
        authorize(canAccessDomain(domain, authProfile)) {
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
            domainUserService.route(authProfile, domain) ~
            domainCollectionService.route(authProfile, domain) ~
            domainModelService.route(authProfile, domain) ~
            domainKeyService.route(authProfile, domain) ~
            domainAdminTokenService.route(authProfile, domain) ~
            domainConfigService.route(authProfile, domain) ~
            domainStatsService.route(authProfile, domain) ~
            domainSecurityService.route(authProfile, domain) ~
            domainSessionService.route(authProfile, domain) ~
            domainUserGroupService.route(authProfile, domain) ~
            domainChatService.route(authProfile, domain)
        }
      }
    }
  }

  private[this] def createDomain(createRequest: CreateDomainRestRequest, authProfile: AuthorizationProfile): Future[RestResponse] = {
    val CreateDomainRestRequest(namespace, id, displayName) = createRequest
    // FIXME check to make sure use has permissions to create domain in this namespace
    val message = CreateDomainRequest(namespace, id, displayName, anonymousAuth = false, authProfile.username)
    (domainStoreActor ? message)
      .map { _ => CreatedResponse }
      .recover {
        case NamespaceNotFoundException(namespace) =>
          namespaceNotFoundResponse(namespace)
      }
  }

  private[this] def getDomains(authProfile: AuthorizationProfile, namespace: Option[String], filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    val message = ListDomainsRequest(authProfile.data, namespace, filter, offset, limit)
    (domainStoreActor ? message)
      .mapTo[ListDomainsResponse]
      .map(_.domains)
      .map(domains =>
        okResponse(
          domains map (domain => DomainRestData(
            domain.displayName,
            domain.domainFqn.namespace,
            domain.domainFqn.domainId,
            domain.status.toString.toLowerCase))))
  }

  private[this] def getDomain(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? GetDomainRequest(namespace, domainId))
      .mapTo[GetDomainResponse]
      .map(_.domain)
      .map {
      case Some(domain) =>
        okResponse(DomainRestData(
          domain.displayName,
          domain.domainFqn.namespace,
          domain.domainFqn.domainId,
          domain.status.toString))
      case None =>
        notFoundResponse()
    }
  }

  private[this] def deleteDomain(namespace: String, domainId: String): Future[RestResponse] = {
    (domainStoreActor ? DeleteDomainRequest(namespace, domainId)) map { _ => DeletedResponse }
  }

  private[this] def updateDomain(namespace: String, domainId: String, request: UpdateDomainRestRequest): Future[RestResponse] = {
    val UpdateDomainRestRequest(displayName) = request
    val message = UpdateDomainRequest(namespace, domainId, displayName)
    (domainStoreActor ? message) map { _ => OkResponse }
  }
}
