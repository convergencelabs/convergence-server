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
import akka.http.scaladsl.server.Directives.{Segment, _enhanceRouteWithConcatenation, _segmentStringToPathMatcher, as, authorize, complete, delete, entity, get, path, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.server.api.rest._
import com.convergencelabs.convergence.server.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.datastore.convergence.DomainRoleTarget
import com.convergencelabs.convergence.server.datastore.convergence.RoleStore.{Role, UserRoles}
import com.convergencelabs.convergence.server.datastore.convergence.RoleStoreActor._
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.security.{AuthorizationProfile, Roles}

import scala.concurrent.{ExecutionContext, Future}

object DomainMembersService {

  case class SetUserRole(role: String)

  case class UserRoleResponse(role: Option[String])

}

class DomainMembersService(private[this] val executionContext: ExecutionContext,
                           private[this] val timeout: Timeout,
                           private[this] val roleStoreActor: ActorRef)
  extends AbstractDomainRestService(executionContext, timeout) {

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
    val message = GetAllUserRolesRequest(DomainRoleTarget(domain))
    (roleStoreActor ? message)
      .mapTo[GetAllUserRolesResponse]
      .map(_.userRoles)
      .map { userRoles =>
        val roleMap = userRoles.map(ur => (ur.username, ur.roles.head.role.name)).toMap
        okResponse(roleMap)
      }
  }

  private[this] def setAllMembers(domain: DomainId, userRoles: Map[String, String], authProfile: AuthorizationProfile): Future[RestResponse] = {
    // Force the current user to be an owner.
    val mapped = userRoles.map { case (username, role) => (username, Set(role)) } +
      (authProfile.username -> Set(Roles.Domain.Owner))

    val message = SetAllUserRolesForTargetRequest(DomainRoleTarget(domain), mapped)
    (roleStoreActor ? message).mapTo[Unit] map (_ => OkResponse)
  }

  private[this] def getRoleForUser(domain: DomainId, username: String): Future[RestResponse] = {
    (roleStoreActor ? GetUserRolesForTargetRequest(username, DomainRoleTarget(domain)))
      .mapTo[GetUserRolesForTargetResponse]
      .map(_.roles)
      .map { roles =>
        val role = roles.toList match {
          case Nil =>
            UserRoleResponse(None)
          case first :: _ =>
            UserRoleResponse(Some(first.name))
        }

        okResponse(role)
      }
  }

  private[this] def setRoleForUser(domain: DomainId, username: String, role: String, authProfile: AuthorizationProfile): Future[RestResponse] = {
    if (username == authProfile.username) {
      Future.successful(forbiddenResponse(Some("You can not set your own user's role.")))
    } else {
      val message = SetUsersRolesForTargetRequest(username, DomainRoleTarget(domain), Set(role))
      (roleStoreActor ? message) map (_ => OkResponse) recover {
        case _: EntityNotFoundException => notFoundResponse()
      }
    }
  }

  private[this] def removeUserRole(domain: DomainId, username: String, authProfile: AuthorizationProfile): Future[RestResponse] = {
    if (username == authProfile.username) {
      Future.successful(forbiddenResponse(Some("You can not remove your own user.")))
    } else {
      val message = RemoveUserFromTarget(DomainRoleTarget(domain), username)
      (roleStoreActor ? message).mapTo[Unit] map (_ => DeletedResponse)
    }
  }
}
