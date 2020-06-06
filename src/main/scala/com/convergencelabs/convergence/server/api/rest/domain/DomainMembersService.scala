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
import com.convergencelabs.convergence.server.datastore.convergence.RoleStoreActor._
import com.convergencelabs.convergence.server.datastore.convergence.{DomainRoleTarget, RoleStoreActor}
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, Roles}

import scala.concurrent.{ExecutionContext, Future}

class DomainMembersService(private[this] val roleStoreActor: ActorRef[RoleStoreActor.Message],
                           private[this] val system: ActorSystem[_],
                           private[this] val executionContext: ExecutionContext,
                           private[this] val timeout: Timeout)
  extends AbstractDomainRestService(system, executionContext, timeout) {

  import DomainMembersService._

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("members") {
      pathEnd {
        get {
          complete(getAllMembers(domain))
        } ~ post {
          authorize(canManageUsers(domain, authProfile)) {
            entity(as[Map[String, String]]) { members =>
              complete(setAllMembers(domain, members, authProfile))
            }
          }
        }
      } ~ path(Segment) { username =>
        get {
          complete(getRoleForUser(domain, username))
        } ~ put {
          authorize(canManageUsers(domain, authProfile)) {
            entity(as[SetUserRole]) { memberRole =>
              complete(setRoleForUser(domain, username, memberRole.role, authProfile))
            }
          }
        } ~ delete {
          authorize(canManageUsers(domain, authProfile)) {
            complete(removeUserRole(domain, username, authProfile))
          }
        }
      }
    }
  }

  private[this] def getAllMembers(domain: DomainId): Future[RestResponse] = {
    roleStoreActor.ask[GetAllUserRolesResponse](GetAllUserRolesRequest(DomainRoleTarget(domain), _))
      .map(_.userRoles.fold(
        {
          case TargetNotFoundError() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { userRoles =>
          val roleMap = userRoles.map(ur => (ur.username, ur.roles.head.role.name)).toMap
          okResponse(roleMap)
        })
      )
  }

  private[this] def setAllMembers(domain: DomainId, userRoles: Map[String, String], authProfile: AuthorizationProfile): Future[RestResponse] = {
    // Force the current user to be an owner.
    val mapped = userRoles.map { case (username, role) => (username, Set(role)) } +
      (authProfile.username -> Set(Roles.Domain.Owner))
    roleStoreActor
      .ask[SetAllUserRolesForTargetResponse](SetAllUserRolesForTargetRequest(DomainRoleTarget(domain), mapped, _))
      .map(_.response.fold(
        {
          case TargetNotFoundError() =>
            notFoundResponse(Some("The specified domain does not exists"))
          case UserNotFoundError() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { _ =>
          OkResponse
        })
      )
  }

  private[this] def getRoleForUser(domain: DomainId, username: String): Future[RestResponse] = {
    roleStoreActor
      .ask[GetUserRolesForTargetResponse](GetUserRolesForTargetRequest(username, DomainRoleTarget(domain), _))
      .map(_.roles.fold(
        {
          case UserNotFoundError() =>
            NotFoundResponse
          case UnknownError() =>
            InternalServerError
        },
        { roles =>
          val r = if (roles.isEmpty) {
            UserRoleResponse(None)
          } else {
            UserRoleResponse(Some(roles.head.name))
          }
          okResponse(r)
        })
      )
  }

  private[this] def setRoleForUser(domain: DomainId, username: String, role: String, authProfile: AuthorizationProfile): Future[RestResponse] = {
    if (username == authProfile.username) {
      Future.successful(forbiddenResponse(Some("You can not set your own user's role.")))
    } else {
      roleStoreActor
        .ask[SetUsersRolesForTargetResponse](SetUsersRolesForTargetRequest(username, DomainRoleTarget(domain), Set(role), _))
        .map(_.response.fold(
          {
            case UserNotFoundError() =>
              NotFoundResponse
            case UnknownError() =>
              InternalServerError
          },
          { _ =>
            OkResponse
          })
        )
    }
  }

  private[this] def removeUserRole(domain: DomainId, username: String, authProfile: AuthorizationProfile): Future[RestResponse] = {
    if (username == authProfile.username) {
      Future.successful(forbiddenResponse(Some("You can not remove your own user.")))
    } else {
      roleStoreActor
        .ask[RemoveUserFromTargetResponse](RemoveUserFromTargetRequest(DomainRoleTarget(domain), username, _))
        .map(_.response.fold(
          {
            case TargetNotFoundError() =>
              notFoundResponse(Some("The specified domain does not exits"))
            case UserNotFoundError() =>
              notFoundResponse(Some("The specified user does not exits"))
            case UnknownError() =>
              InternalServerError
          },
          { _ =>
            DeletedResponse
          })
        )
    }
  }
}

object DomainMembersService {

  case class SetUserRole(role: String)

  case class UserRoleResponse(role: Option[String])

}