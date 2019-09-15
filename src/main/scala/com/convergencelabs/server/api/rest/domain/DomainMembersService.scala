package com.convergencelabs.server.api.rest.domain

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.api.rest.OkResponse
import com.convergencelabs.server.api.rest.RestResponse
import com.convergencelabs.server.api.rest.notFoundResponse
import com.convergencelabs.server.api.rest.okResponse
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.convergence.DomainRoleTarget
import com.convergencelabs.server.datastore.convergence.RoleStore.Role
import com.convergencelabs.server.datastore.convergence.RoleStore.UserRoles
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.GetAllUserRolesRequest
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.GetUserRolesForTargetRequest
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.RemoveUserFromTarget
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.SeUsersRolesForTargetRequest
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.SetAllUserRolesForTargetRequest
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.security.AuthorizationProfile

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Route
import akka.util.Timeout

object DomainSecurityService {
  case class SetUserRole(role: String)
  case class UserRoleResponse(role: Option[String])
}

class DomainSecurityService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val roleStoreActor: ActorRef)
  extends DomainRestService(executionContext, timeout) {

  import DomainSecurityService._
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
            complete(setRoleForUesr(domain, username, memberRole.role))
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

  def setRoleForUesr(domain: DomainId, username: String, role: String): Future[RestResponse] = {
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
