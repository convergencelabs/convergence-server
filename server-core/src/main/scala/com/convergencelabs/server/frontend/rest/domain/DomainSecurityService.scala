package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.Permission
import com.convergencelabs.server.datastore.PermissionsStoreActor.GetAllUserRolesRequest
import com.convergencelabs.server.datastore.PermissionsStoreActor.GetUserPermissionsRequest
import com.convergencelabs.server.datastore.PermissionsStoreActor.GetUserRolesRequest
import com.convergencelabs.server.datastore.PermissionsStoreActor.SetRolesRequest
import com.convergencelabs.server.datastore.UserRoles
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.rest.AuthorizationActor.ConvergenceAuthorizedRequest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.authorizeAsync
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.put
import akka.http.scaladsl.server.Directives.Segment
import akka.http.scaladsl.server.Route
import akka.util.Timeout


object DomainSecurityService {
  case class SetUserRolesRequest(roles: List[String])

  case class GetAllUserRolesRestResponse(userRoles: Set[UserRoles]) extends AbstractSuccessResponse
  case class GetUserRolesRestResponse(userRoles: UserRoles) extends AbstractSuccessResponse
  case class GetUserPermissionsRestResponse(permissions: Set[Permission]) extends AbstractSuccessResponse
}

class DomainSecurityService(
    private[this] val executionContext: ExecutionContext,
    private[this] val timeout: Timeout,
    private[this] val authActor: ActorRef,
    private[this] val permissionStoreActor: ActorRef) 
    extends DomainRestService(executionContext, timeout, authActor) {

  import akka.pattern.ask
  import DomainSecurityService._
  
  def route(convergenceUsername: String, domain: DomainFqn): Route = {
    pathPrefix("permissions") {
      pathPrefix("roles") {
        pathEnd {
          get {
            authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
              complete(getAllUserRolesRequest(domain))
            }
          }
        } ~ pathPrefix(Segment) { username =>
          pathEnd {
            get {
              authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
                complete(getRolesByUsername(username, domain))
              }
            } ~ put {
              entity(as[SetUserRolesRequest]) { request =>
                authorizeAsync(canAdministerDomain(domain, convergenceUsername)) {
                  complete(setUserRolesRequest(username, request, domain))
                }
              }
            }
          }
        }
      } ~ pathPrefix(Segment) { username =>
        pathEnd {
          get {
            authorizeAsync(canAccessDomain(domain, convergenceUsername)) {
              complete(getPermissionsByUsername(username, domain))
            }
          }
        }
      }
    }
  }

  def getAllUserRolesRequest(domain: DomainFqn): Future[RestResponse] = {
    (permissionStoreActor ? GetAllUserRolesRequest(domain)).mapTo[Set[UserRoles]] map
      (userRoles => (StatusCodes.OK, GetAllUserRolesRestResponse(userRoles)))
  }

  def getRolesByUsername(username: String, domain: DomainFqn): Future[RestResponse] = {
    (permissionStoreActor ? GetUserRolesRequest(username, domain)).mapTo[UserRoles] map {
      userRoles => (StatusCodes.OK, GetUserRolesRestResponse(userRoles))
    }
  }

  def getPermissionsByUsername(username: String, domain: DomainFqn): Future[RestResponse] = {
    (permissionStoreActor ? GetUserPermissionsRequest(username, domain)).mapTo[Set[Permission]] map {
      permissions => (StatusCodes.OK, GetUserPermissionsRestResponse(permissions))
    }
  }

  def setUserRolesRequest(username: String, updateRequest: SetUserRolesRequest, domain: DomainFqn): Future[RestResponse] = {
    val SetUserRolesRequest(roles) = updateRequest
    val message = SetRolesRequest(username, domain, roles)
    (permissionStoreActor ? message) map { _ => OkResponse } recover {
      case _: EntityNotFoundException => NotFoundError
    }
  }

  // Permission Checks

  def canAdministerDomain(domainFqn: DomainFqn, username: String): Future[Boolean] = {
    (authorizationActor ? ConvergenceAuthorizedRequest(username, domainFqn, Set("manage-permissions"))).mapTo[Try[Boolean]].map(_.get)
  }
}
