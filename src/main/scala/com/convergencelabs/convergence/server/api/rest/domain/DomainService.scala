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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.datastore.convergence.DomainStoreActor._
import com.convergencelabs.convergence.server.datastore.convergence.{DomainStoreActor, NamespaceNotFoundException, RoleStoreActor}
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.chat.ChatActor
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

class DomainService(private[this] val system: ActorSystem[_],
                    private[this] val executionContext: ExecutionContext,
                    private[this] val domainStoreActor: ActorRef[DomainStoreActor.Message],
                    private[this] val domainRestActor: ActorRef[DomainRestActor.Message],
                    private[this] val roleStoreActor: ActorRef[RoleStoreActor.Message],
                    private[this] val modelClusterRegion: ActorRef[RealtimeModelActor.Message],
                    private[this] val chatClusterRegion: ActorRef[ChatActor.Message],
                    private[this] val defaultTimeout: Timeout)
  extends AbstractDomainRestService(system, executionContext, defaultTimeout) {

  import DomainService._

  val domainConfigService = new DomainConfigService(domainRestActor, system, ec, t)
  val domainUserService = new DomainUserService(domainRestActor, system, ec, t)
  val domainUserGroupService = new DomainUserGroupService(domainRestActor, system, ec, t)
  val domainStatsService = new DomainStatsService(domainRestActor, system, ec, t)
  val domainCollectionService = new DomainCollectionService(domainRestActor, system, ec, t)
  val domainSessionService = new DomainSessionService(domainRestActor, system, ec, t)
  val domainModelService = new DomainModelService(domainRestActor, modelClusterRegion, system, ec, t)
  val domainKeyService = new DomainKeyService(domainRestActor, system, ec, t)
  val domainAdminTokenService = new DomainAdminTokenService(domainRestActor, system, ec, t)
  val domainChatService = new DomainChatService(domainRestActor, chatClusterRegion, system, ec, t)
  val domainSecurityService = new DomainMembersService(roleStoreActor, system, ec, t)

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("domains") {
      pathEnd {
        get {
          parameters("namespace".?, "filter".?, "offset".as[Int].?, "limit".as[Int].?) { (namespace, filter, offset, limit) =>
            complete(getDomains(authProfile, namespace, filter, offset, limit))
          }
        } ~ post {
          entity(as[CreateDomainRestRequestData]) { request =>
            authorize(canManageDomains(request.namespace, authProfile)) {
              complete(createDomain(request, authProfile))
            }
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
              entity(as[UpdateDomainRestRequestData]) { request =>
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

  private[this] def createDomain(createRequest: CreateDomainRestRequestData, authProfile: AuthorizationProfile): Future[RestResponse] = {
    val CreateDomainRestRequestData(namespace, id, displayName) = createRequest
    domainStoreActor.ask[CreateDomainResponse](CreateDomainRequest(namespace, id, displayName, anonymousAuth = false, authProfile.username, _)).flatMap {
      case DomainStoreActor.CreateDomainSuccess(_) =>
        Future.successful(CreatedResponse)
      case RequestFailure(cause) =>
        cause match {
          case NamespaceNotFoundException(namespace) =>
            Future.successful(namespaceNotFoundResponse(namespace))
          case _ =>
            Future.failed(cause)
        }
    }
  }

  private[this] def getDomains(authProfile: AuthorizationProfile, namespace: Option[String], filter: Option[String], offset: Option[Int], limit: Option[Int]): Future[RestResponse] = {
    domainStoreActor.ask[ListDomainsResponse](ListDomainsRequest(authProfile.data, namespace, filter, offset, limit, _)).flatMap {
      case ListDomainsSuccess(domains) =>
        val response = okResponse(
          domains map (domain => DomainRestData(
            domain.displayName,
            domain.domainFqn.namespace,
            domain.domainFqn.domainId,
            domain.status.toString.toLowerCase)))
        Future.successful(response)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def getDomain(namespace: String, domainId: String): Future[RestResponse] = {
    domainStoreActor.ask[GetDomainResponse](GetDomainRequest(namespace, domainId, _)).flatMap {
      case GetDomainSuccess(Some(domain)) =>
        val response = okResponse(DomainRestData(
            domain.displayName,
            domain.domainFqn.namespace,
            domain.domainFqn.domainId,
            domain.status.toString.toLowerCase()))
        Future.successful(response)
      case GetDomainSuccess(None) =>
        Future.successful(notFoundResponse())
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def deleteDomain(namespace: String, domainId: String): Future[RestResponse] = {
    domainStoreActor.ask[DeleteDomainResponse](DeleteDomainRequest(namespace, domainId, _)).flatMap {
      case RequestSuccess() =>
        Future.successful(DeletedResponse)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }

  private[this] def updateDomain(namespace: String, domainId: String, request: UpdateDomainRestRequestData): Future[RestResponse] = {
    val UpdateDomainRestRequestData(displayName) = request
    domainStoreActor.ask[UpdateDomainResponse](UpdateDomainRequest(namespace, domainId, displayName, _)).flatMap {
      case RequestSuccess() =>
        Future.successful(DeletedResponse)
      case RequestFailure(cause) =>
        Future.failed(cause)
    }
  }
}

object DomainService {

  case class CreateDomainRestRequestData(namespace: String, id: String, displayName: String)

  case class UpdateDomainRestRequestData(displayName: String)

}
