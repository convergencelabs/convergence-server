package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.GetAllUserRolesRequest
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.GetUserPermissionsRequest
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.GetUserRolesForTargetRequest
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.SetRolesRequest
import com.convergencelabs.server.datastore.convergence.RoleStore.UserRoles
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.convergence.DomainRoleTarget

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.authorize
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.convergencelabs.server.security.AuthorizationProfile


object DomainSecurityService {
  case class SetUserRolesRequest(roles: Set[String])

  case class GetAllUserRolesRestResponse(userRoles: Set[UserRoles])
  case class GetUserRolesRestResponse(userRoles: UserRoles)
  case class GetUserPermissionsRestResponse(permissions: Set[String])
}

class DomainSecurityService(
  private[this] val executionContext: ExecutionContext,
  private[this] val timeout: Timeout,
  private[this] val permissionStoreActor: ActorRef)
    extends DomainRestService(executionContext, timeout) {

  import akka.pattern.ask
  import DomainSecurityService._

  def route(authProfile: AuthorizationProfile, domain: DomainFqn): Route = {
    pathPrefix("security") {
      pathPrefix("roles") {
        (pathEnd & get) {
          authorize(canAccessDomain(domain, authProfile)) {
            complete(getAllUserRolesRequest(domain))
          }
        } ~ path(Segment) { username =>
          get {
            authorize(canAccessDomain(domain, authProfile)) {
              complete(getRolesByUsername(username, domain))
            }
          } ~ put {
            entity(as[SetUserRolesRequest]) { request =>
              authorize(canManageUsers(domain, authProfile)) {
                complete(setUserRolesRequest(username, request, domain))
              }
            }
          }
        }
      } ~ (path("permissions" / Segment) & get) { username =>
        authorize(canAccessDomain(domain, authProfile)) {
          complete(getPermissionsByUsername(username, domain))
        }
      }
    }
  }

  def getAllUserRolesRequest(domain: DomainFqn): Future[RestResponse] = {
    (permissionStoreActor ? GetAllUserRolesRequest(DomainRoleTarget(domain))).mapTo[Set[UserRoles]] map
      (userRoles => okResponse(GetAllUserRolesRestResponse(userRoles)))
  }

  def getRolesByUsername(username: String, domain: DomainFqn): Future[RestResponse] = {
    (permissionStoreActor ? GetUserRolesForTargetRequest(username, DomainRoleTarget(domain))).mapTo[UserRoles] map {
      userRoles => okResponse(GetUserRolesRestResponse(userRoles))
    }
  }

  def getPermissionsByUsername(username: String, domain: DomainFqn): Future[RestResponse] = {
    (permissionStoreActor ? GetUserPermissionsRequest(username, DomainRoleTarget(domain))).mapTo[Set[String]] map {
      permissions => okResponse(GetUserPermissionsRestResponse(permissions))
    }
  }

  def setUserRolesRequest(username: String, updateRequest: SetUserRolesRequest, domain: DomainFqn): Future[RestResponse] = {
    val SetUserRolesRequest(roles) = updateRequest
    val message = SetRolesRequest(username, DomainRoleTarget(domain), roles)
    (permissionStoreActor ? message) map { _ => OkResponse } recover {
      case _: EntityNotFoundException => notFoundResponse()
    }
  }
}
