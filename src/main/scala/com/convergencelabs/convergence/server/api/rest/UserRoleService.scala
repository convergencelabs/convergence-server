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

package com.convergencelabs.convergence.server.api.rest

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Scheduler}
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, as, authorize, complete, concat, delete, entity, get, path, pathEnd, pathPrefix, post}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.convergence.server.backend.services.server.RoleStoreActor._
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.role
import com.convergencelabs.convergence.server.model.server.role._
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, Permissions}

import scala.concurrent.{ExecutionContext, Future}


private[rest] class UserRoleService(roleActor: ActorRef[Message],
                                    scheduler: Scheduler,
                                    executionContext: ExecutionContext,
                                    defaultTimeout: Timeout) extends JsonSupport {

  private[this] implicit val ec: ExecutionContext = executionContext
  private[this] implicit val t: Timeout = defaultTimeout
  private[this] implicit val s: Scheduler = scheduler

  val route: AuthorizationProfile => Route = { authProfile: AuthorizationProfile =>
    pathPrefix("roles") {
      concat(
        pathPrefix("server") {
          authorize(canManageUsers(authProfile)) {
            pathEnd {
              get {
                complete(getUserRolesForTarget(ServerRoleTarget()))
              } ~ post {
                entity(as[Map[String, String]]) { userRoles =>
                  complete(updateUserRolesForTarget(ServerRoleTarget(), userRoles))
                }
              }
            } ~ (delete & path(Segment)) { username =>
              complete(deleteUserRoleForTarget(ServerRoleTarget(), username))
            }
          }
        },
        pathPrefix("namespace" / Segment) { namespace =>
          authorize(canManageNamespaceUsers(namespace, authProfile)) {
            pathEnd {
              get {
                complete(getUserRolesForTarget(NamespaceRoleTarget(namespace)))
              } ~ post {
                entity(as[Map[String, String]]) { userRoles =>
                  complete(updateUserRolesForTarget(NamespaceRoleTarget(namespace), userRoles))
                }
              }
            } ~ path(Segment) { username =>
              complete(deleteUserRoleForTarget(NamespaceRoleTarget(namespace), username))
            }
          }
        },
        path("domain" / Segment / Segment) { (namespace, domain) =>
          authorize(canManageDomainUsers(namespace, domain, authProfile)) {
            pathEnd {
              get {
                complete(getUserRolesForTarget(DomainRoleTarget(DomainId(namespace, domain))))
              } ~ post {
                entity(as[Map[String, String]]) { userRoles =>
                  complete(updateUserRolesForTarget(role.DomainRoleTarget(DomainId(namespace, domain)), userRoles))
                }
              }
            } ~ path(Segment) { username =>
              complete(deleteUserRoleForTarget(role.DomainRoleTarget(DomainId(namespace, domain)), username))
            }
          }
        })
    }
  }

  private[this] def getUserRolesForTarget(target: RoleTarget): Future[RestResponse] = {
    roleActor
      .ask[GetAllUserRolesResponse](GetAllUserRolesRequest(target, _))
      .map(_.userRoles.fold(
        {
          case TargetNotFoundError() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { userRoles =>
          val response = userRoles.filter(_.roles.nonEmpty)
            .map(userRole => (userRole.username, userRole.roles.head.role.name))
          okResponse(response)
        })
      )
  }

  private[this] def updateUserRolesForTarget(target: RoleTarget, userRoles: Map[String, String]): Future[RestResponse] = {
    val mappedRoles = userRoles.map(entry => entry._1 -> Set(entry._2))
    roleActor
      .ask[UpdateRolesForTargetResponse](UpdateRolesForTargetRequest(target, mappedRoles, _))
      .map(_.response.fold(
        {
          case UserNotFoundError() =>
            NotFoundResponse
          case TargetNotFoundError() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          OkResponse
        })
      )
  }

  private[this] def deleteUserRoleForTarget(target: RoleTarget, username: String): Future[RestResponse] = {
    roleActor
      .ask[RemoveUserFromTargetResponse](RemoveUserFromTargetRequest(target, username, _))
      .map(_.response.fold(
        {
          case TargetNotFoundError() =>
            notFoundResponse(Some("The target was not found"))
          case UserNotFoundError() =>
            notFoundResponse(Some("The user was not found"))
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          DeletedResponse
        })
      )
  }

  private[this] def canManageUsers(authProfile: AuthorizationProfile): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Server.ManageUsers)
  }

  private[this] def canManageNamespaceUsers(namespace: String, authProfile: AuthorizationProfile): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Server.ManageDomains) ||
      authProfile.hasNamespacePermission(Permissions.Namespace.ManageUsers, namespace)
  }

  private[this] def canManageDomainUsers(namespace: String, domain: String, authProfile: AuthorizationProfile): Boolean = {
    authProfile.hasGlobalPermission(Permissions.Server.ManageDomains) ||
      authProfile.hasNamespacePermission(Permissions.Namespace.ManageUsers, namespace) ||
      authProfile.hasDomainPermission(Permissions.Domain.ManageUsers, namespace, domain)
  }
}


private[rest] object UserRoleService {

  case class CreateNamespacePost(id: String, displayName: String)

  case class UpdateNamespacePut(displayName: String)

  case class NamespaceRestData(id: String, displayName: String)

  case class NamespaceAndDomainsRestData(id: String, displayName: String, domains: Set[DomainRestData])

  case class UserRoleData(username: String, role: String)
}
