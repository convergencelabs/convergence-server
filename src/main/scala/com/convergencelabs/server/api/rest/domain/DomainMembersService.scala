/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.api.rest.domain

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.{addByNameNullaryApply, addDirectiveApply}
import akka.http.scaladsl.server.Directives.{_enhanceRouteWithConcatenation, _segmentStringToPathMatcher, as, complete, delete, entity, get, path, pathEnd, pathPrefix, post, put}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.api.rest.{OkResponse, RestResponse, notFoundResponse, okResponse}
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.convergence.DomainRoleTarget
import com.convergencelabs.server.datastore.convergence.RoleStore.{Role, UserRoles}
import com.convergencelabs.server.datastore.convergence.RoleStoreActor._
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.security.AuthorizationProfile

import scala.concurrent.{ExecutionContext, Future}

object DomainMembersService {
  case class SetUserRole(role: String)
  case class UserRoleResponse(role: Option[String])
}

class DomainMembersService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val roleStoreActor: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainMembersService._
  import akka.http.scaladsl.server.Directives.Segment
  import akka.pattern.ask

  def route(authProfile: AuthorizationProfile, domain: DomainId): Route = {
    pathPrefix("members") {
      pathEnd {
        get {
          complete(getAllMembers(domain))
        } ~ post {
          entity(as[Map[String, String]]) { members =>
            complete(setAllMembers(domain, members))
          }
        }
      } ~ path(Segment) { username =>
        get {
          complete(getRoleForUser(domain, username))
        } ~ put {
          entity(as[SetUserRole]) { memberRole =>
            complete(setRoleForUser(domain, username, memberRole.role))
          }
        } ~ delete {
          complete(removeUserRole(domain, username))
        }
      }
    }
  }

  def getAllMembers(domain: DomainId): Future[RestResponse] = {
    val message = GetAllUserRolesRequest(DomainRoleTarget(domain))
    (roleStoreActor ? message)
      .mapTo[Set[UserRoles]]
      .map { userRoles =>
        val roleMap = userRoles.map(ur => (ur.username, ur.roles.head.role.name)).toMap
        okResponse(roleMap)
      }
  }

  def setAllMembers(domain: DomainId, userRoles: Map[String, String]): Future[RestResponse] = {
    val mapped = userRoles.map { case (username, role) => (username, Set(role)) }
    val message = SetAllUserRolesForTargetRequest(DomainRoleTarget(domain), mapped)
    (roleStoreActor ? message).mapTo[Unit] map (_ => OkResponse)
  }

  def getRoleForUser(domain: DomainId, username: String): Future[RestResponse] = {
    (roleStoreActor ? GetUserRolesForTargetRequest(username, DomainRoleTarget(domain))).mapTo[Set[Role]] map { roles =>
      val role = roles.toList match {
        case Nil =>
          UserRoleResponse(None)
        case first :: _ =>
          UserRoleResponse(Some(first.name))
      }

      okResponse(role)
    }
  }

  def setRoleForUser(domain: DomainId, username: String, role: String): Future[RestResponse] = {
    val message = SeUsersRolesForTargetRequest(username, DomainRoleTarget(domain), Set(role))
    (roleStoreActor ? message) map { _ => OkResponse } recover {
      case _: EntityNotFoundException => notFoundResponse()
    }
  }

  def removeUserRole(domain: DomainId, username: String): Future[RestResponse] = {
    val message = RemoveUserFromTarget(DomainRoleTarget(domain), username)
    (roleStoreActor ? message).mapTo[Unit] map (_ => OkResponse)
  }
}
