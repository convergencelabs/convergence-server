package com.convergencelabs.server.frontend.rest

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.convergencelabs.server.datastore.convergence.DomainRoleTarget
import com.convergencelabs.server.datastore.convergence.NamespaceRoleTarget
import com.convergencelabs.server.datastore.convergence.RoleStore.UserRoles
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.GetAllUserRolesRequest
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.RemoveUserFromTarget
import com.convergencelabs.server.datastore.convergence.RoleStoreActor.UpdateRolesForTargetRequest
import com.convergencelabs.server.datastore.convergence.RoleTarget
import com.convergencelabs.server.datastore.convergence.ServerRoleTarget
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.security.AuthorizationProfile

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._enhanceRouteWithConcatenation
import akka.http.scaladsl.server.Directives._segmentStringToPathMatcher
import akka.http.scaladsl.server.Directives.as
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Directives.delete
import akka.http.scaladsl.server.Directives.entity
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Directives.pathEnd
import akka.http.scaladsl.server.Directives.pathPrefix
import akka.http.scaladsl.server.Directives.post
import akka.util.Timeout

object RoleService {
  case class CreateNamespacePost(id: String, displayName: String)
  case class UpdateNamespacePut(displayName: String)
  case class NamespaceRestData(id: String, displayName: String)
  case class NamespaceAndDomainsRestData(id: String, displayName: String, domains: Set[DomainRestData])

  case class UserRoleData(username: String, role: String)
}

class RoleService(
  private[this] val executionContext: ExecutionContext,
  private[this] val roleActor: ActorRef,
  private[this] val defaultTimeout: Timeout) extends JsonSupport {

  import RoleService._
  import akka.http.scaladsl.server.Directives.Segment
  import akka.pattern.ask

  implicit val ec = executionContext
  implicit val t = defaultTimeout

  val route = { authProfile: AuthorizationProfile =>
    pathPrefix("roles") {
      concat(
        pathPrefix("server") {
          pathEnd {
            get {
              complete(getUserRolesForTarget(authProfile, ServerRoleTarget))
            } ~ post {
              entity(as[Map[String, String]]) { userRoles =>
                complete(updateUserRolesForTarget(authProfile, ServerRoleTarget, userRoles))
              }
            }
          } ~ (delete & path(Segment)) { username =>
            complete(deleteUserRoleForTarget(authProfile, ServerRoleTarget, username))
          }
        },
        pathPrefix("namespace" / Segment) { namespace =>
          pathEnd {
            get {
              complete(getUserRolesForTarget(authProfile, NamespaceRoleTarget(namespace)))
            } ~ post {
              entity(as[Map[String, String]]) { userRoles =>
                complete(updateUserRolesForTarget(authProfile, NamespaceRoleTarget(namespace), userRoles))
              }
            }
          } ~ path(Segment) { username =>
            complete(deleteUserRoleForTarget(authProfile, NamespaceRoleTarget(namespace), username))
          }
        },
        path("domain" / Segment / Segment) { (namespace, domain) =>
          pathEnd {
            get {
              complete(getUserRolesForTarget(authProfile, DomainRoleTarget(new DomainFqn(namespace, domain))))
            } ~ post {
              entity(as[Map[String, String]]) { userRoles =>
                complete(updateUserRolesForTarget(authProfile, DomainRoleTarget(new DomainFqn(namespace, domain)), userRoles))
              }
            }
          } ~ path(Segment) { username =>
            complete(deleteUserRoleForTarget(authProfile, DomainRoleTarget(new DomainFqn(namespace, domain)), username))
          }
        })
    }
  }

  def getUserRolesForTarget(authProfile: AuthorizationProfile, target: RoleTarget): Future[RestResponse] = {
    val request = GetAllUserRolesRequest(target)
    (roleActor ? request).mapTo[Set[UserRoles]] map { userRoles =>
      val response = userRoles
        .filter(!_.roles.isEmpty)
        .map(userRole => (userRole.username, userRole.roles.head.role.name))
        .toMap
      okResponse(response)
    }
  }

  def updateUserRolesForTarget(authProfile: AuthorizationProfile, target: RoleTarget, userRoles: Map[String, String]): Future[RestResponse] = {
    val request = UpdateRolesForTargetRequest(target, userRoles.map(entry => (entry._1 -> Set(entry._2))))
    (roleActor ? request).mapTo[Unit] map (_ => OkResponse)
  }

  def deleteUserRoleForTarget(authProfile: AuthorizationProfile, target: RoleTarget, username: String): Future[RestResponse] = {
    val request = RemoveUserFromTarget(target, username)
    (roleActor ? request).mapTo[Unit] map (_ => OkResponse)
  }
}
