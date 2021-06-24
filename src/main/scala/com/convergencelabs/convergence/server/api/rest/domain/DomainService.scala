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
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor
import com.convergencelabs.convergence.server.backend.services.domain.chat.ChatActor
import com.convergencelabs.convergence.server.backend.services.domain.model.RealtimeModelActor
import com.convergencelabs.convergence.server.backend.services.domain.rest.DomainRestActor
import com.convergencelabs.convergence.server.backend.services.server.DomainStoreActor._
import com.convergencelabs.convergence.server.backend.services.server.{DomainStoreActor, RoleStoreActor}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.DomainAvailability
import com.convergencelabs.convergence.server.security.AuthorizationProfile
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}

import scala.concurrent.{ExecutionContext, Future}

private[rest] final class DomainService(schedule: Scheduler,
                                        executionContext: ExecutionContext,
                                        domainStoreActor: ActorRef[DomainStoreActor.Message],
                                        domainRestActor: ActorRef[DomainRestActor.Message],
                                        roleStoreActor: ActorRef[RoleStoreActor.Message],
                                        modelClusterRegion: ActorRef[RealtimeModelActor.Message],
                                        chatClusterRegion: ActorRef[ChatActor.Message],
                                        activityCluster: ActorRef[ActivityActor.Message],
                                        defaultTimeout: Timeout)
  extends AbstractDomainRestService(schedule, executionContext, defaultTimeout) {

  import DomainService._

  val domainConfigService = new DomainConfigService(domainRestActor, schedule, ec, t)
  val domainUserService = new DomainUserService(domainRestActor, schedule, ec, t)
  val domainUserGroupService = new DomainUserGroupService(domainRestActor, schedule, ec, t)
  val domainStatsService = new DomainStatsService(domainRestActor, schedule, ec, t)
  val domainCollectionService = new DomainCollectionService(domainRestActor, schedule, ec, t)
  val domainSessionService = new DomainSessionService(domainRestActor, schedule, ec, t)
  val domainModelService = new DomainModelService(domainRestActor, modelClusterRegion, schedule, ec, t)
  val domainKeyService = new DomainKeyService(domainRestActor, schedule, ec, t)
  val domainAdminTokenService = new DomainAdminTokenService(domainRestActor, schedule, ec, t)
  val domainChatService = new DomainChatService(domainRestActor, chatClusterRegion, schedule, ec, t)
  val domainActivityService = new DomainActivityService(domainRestActor, activityCluster, schedule, ec, t)
  val domainSecurityService = new DomainMembersService(roleStoreActor, schedule, ec, t)

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("domains") {
      pathEnd {
        get {
          parameters("namespace".?, "filter".?, "offset".as[Long].?, "limit".as[Long].?) { (namespace, filter, offset, limit) =>
            complete(getDomains(authProfile, namespace, filter, offset, limit))
          }
        } ~ post {
          entity(as[CreateDomainRestRequestData]) { request =>
            authorize(canManageDomainsInNamespace(request.namespace, authProfile)) {
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
          } ~ path("availability") {
            put {
              entity(as[SetDomainAvailabilityRestRequestData]) { request =>
                complete(setDomainAvailability(namespace, domainId, request))
              }
            }
          } ~ path("id") {
            put {
              entity(as[SetDomainIdData]) { request =>
                complete(setDomainId(namespace, domainId, request))
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
            domainChatService.route(authProfile, domain) ~
            domainActivityService.route(authProfile, domain)
        }
      }
    }
  }

  private[this] def createDomain(createRequest: CreateDomainRestRequestData, authProfile: AuthorizationProfile): Future[RestResponse] = {
    val CreateDomainRestRequestData(namespace, id, displayName) = createRequest
    domainStoreActor.ask[CreateDomainResponse](
      CreateDomainRequest(namespace, id, displayName, anonymousAuth = false, authProfile.username, _))
      .map(_.dbInfo.fold(
        {
          case DomainAlreadyExistsError(field) =>
            conflictsResponse(field, "A domain with this value already exists")
          case InvalidDomainCreationRequest(message) =>
            badRequest(message)
          case UnknownError() =>
            InternalServerError
        },
        { _ => CreatedResponse }
      ))
  }

  private[this] def getDomains(authProfile: AuthorizationProfile,
                               namespace: Option[String],
                               filter: Option[String],
                               offset: Option[Long],
                               limit: Option[Long]): Future[RestResponse] = {
    domainStoreActor
      .ask[GetDomainsResponse](
        GetDomainsRequest(authProfile.data, namespace, filter, QueryOffset(offset), QueryLimit(limit), _))
      .map(_.domains.fold(
        _ => InternalServerError,
        { domains =>
          val response = okResponse(
            domains map { domain =>
              DomainRestData(
                domain.displayName,
                domain.domainId.namespace,
                domain.domainId.domainId,
                domain.availability.toString,
                domain.status.toString,
                domain.statusMessage,
                None
              )
            })
          response
        }
      ))
  }

  private[this] def getDomain(namespace: String, domainId: String): Future[RestResponse] = {
    domainStoreActor.ask[GetDomainAndSchemaVersionResponse](
      GetDomainAndSchemaVersionRequest(namespace, domainId, _))
      .map(_.domain.fold(
        {
          case DomainNotFound() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { case DomainAndSchemaVersion(domain, version) =>
          okResponse(DomainRestData(
            domain.displayName,
            domain.domainId.namespace,
            domain.domainId.domainId,
            domain.availability.toString,
            domain.status.toString,
            domain.statusMessage,
            Some(version)
          ))
        }
      ))
  }

  private[this] def deleteDomain(namespace: String, domainId: String): Future[RestResponse] = {
    domainStoreActor.ask[DeleteDomainResponse](DeleteDomainRequest(namespace, domainId, _))
      .map(_.response.fold(
        {
          case DomainNotFound() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          DeletedResponse
        }
      ))
  }

  private[this] def updateDomain(namespace: String, domainId: String, request: UpdateDomainRestRequestData): Future[RestResponse] = {
    val UpdateDomainRestRequestData(displayName) = request
    domainStoreActor.ask[UpdateDomainResponse](UpdateDomainRequest(namespace, domainId, displayName, _))
      .map(_.response.fold(
        {
          case DomainNotFound() =>
            NotFoundResponse
          case DomainAlreadyExistsError(field) =>
            conflictsResponse(field, s"Can't update the domain because a domain with this value for '$field' already exists.")
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          OkResponse
        }
      ))
  }

  private[this] def setDomainAvailability(namespace: String, domainId: String, request: SetDomainAvailabilityRestRequestData): Future[RestResponse] = {
    val SetDomainAvailabilityRestRequestData(availabilityString) = request
    DomainAvailability.withNameOpt(availabilityString).map { availability =>
      domainStoreActor.ask[SetDomainAvailabilityResponse](
        SetDomainAvailabilityRequest(DomainId(namespace, domainId), availability, _))
        .map(_.response.fold(
          {
            case DomainNotFound() =>
              NotFoundResponse
            case InvalidDomainAvailability() =>
              invalidAvailability(availabilityString)
            case UnknownError() =>
              InternalServerError
          },
          { _ =>
            OkResponse
          }
        ))
    }.getOrElse {
      Future.successful(invalidAvailability(availabilityString))
    }
  }

  private[this] def setDomainId(namespace: String, domainId: String, request: SetDomainIdData): Future[RestResponse] = {
    val SetDomainIdData(id) = request
    domainStoreActor.ask[SetDomainIdResponse](SetDomainIdRequest(DomainId(namespace, domainId), id, _))
      .map(_.response.fold(
        {
          case DomainNotFound() =>
            NotFoundResponse
          case DomainAlreadyExistsError(field) =>
            duplicateResponse(field)
          case DomainNotOfflineError() =>
            (StatusCodes.BadRequest, ErrorResponseEntity("domain_not_offline", Some("Can not set the id of a domain unless it is offline.")))
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          OkResponse
        }
      ))
  }

  private[this] def invalidAvailability(status: String): RestResponse = {
    invalidValueResponse(s"Invalid availability value '$status'.", Some("availability"))
  }
}

object DomainService {

  final case class CreateDomainRestRequestData(namespace: String, id: String, displayName: String)

  final case class UpdateDomainRestRequestData(displayName: String)

  final case class SetDomainAvailabilityRestRequestData(availability: String)

  final case class SetDomainIdData(id: String)

}
