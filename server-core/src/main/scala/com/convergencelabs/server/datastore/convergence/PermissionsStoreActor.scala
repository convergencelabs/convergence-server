package com.convergencelabs.server.datastore.convergence

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.convergence.PermissionsStore.Permission
import com.convergencelabs.server.datastore.convergence.PermissionsStore.Role

import akka.actor.ActorLogging
import akka.actor.Props
import akka.util.Timeout

object PermissionsStoreActor {
  val RelativePath = "PermissionsStoreActor"
  
  def props(dbProvider: DatabaseProvider): Props = Props(new PermissionsStoreActor(dbProvider))
  
  case class CreatePermissionRequest(permission: Permission)
  case class CreateRoleRequest(role: Role)
  case class SetRolesRequest(username: String, domainFqn: DomainFqn, roles: List[String])

  case class GetPermissionsProfileRequest(domainFqn: DomainFqn, username: String)
  case class GetAllUserRolesRequest(domainFqn: DomainFqn)
  case class GetUserRolesRequest(username: String, domainFqn: DomainFqn)
  case class GetUserPermissionsRequest(username: String, domainFqn: DomainFqn)
}

class PermissionsStoreActor private[datastore] (private[this] val dbProvider: DatabaseProvider) extends StoreActor
    with ActorLogging {

  import PermissionsStoreActor._
  
  // FIXME: Read this from configuration
  private[this] implicit val requstTimeout = Timeout(2 seconds)
  private[this] implicit val exectionContext = context.dispatcher

  private[this] val permissionsStore: PermissionsStore = new PermissionsStore(dbProvider)

  def receive: Receive = {
    case message: CreatePermissionRequest      => createPermission(message)
    case message: CreateRoleRequest            => createRole(message)
    case message: SetRolesRequest              => setRolesRequest(message)
    case message: GetPermissionsProfileRequest => getPermissionsProfile(message)
    case message: GetAllUserRolesRequest       => getAllUserRoles(message)
    case message: GetUserRolesRequest          => getUserRoles(message)
    case message: GetUserPermissionsRequest    => getUserPermissions(message)
    case message: Any                          => unhandled(message)

  }

  def createPermission(message: CreatePermissionRequest): Unit = {
    val CreatePermissionRequest(permission) = message
    reply(permissionsStore.createPermission(permission))
  }

  def createRole(message: CreateRoleRequest): Unit = {
    val CreateRoleRequest(role) = message
    reply(permissionsStore.createRole(role))
  }

  def setRolesRequest(message: SetRolesRequest): Unit = {
    val SetRolesRequest(username, domainFqn, roles) = message
    val currentRoles = permissionsStore.getAllUserRoles(domainFqn).get
    reply(permissionsStore.setUserRoles(username, domainFqn, roles))
  }

  def getPermissionsProfile(message: GetPermissionsProfileRequest): Unit = {
    val GetPermissionsProfileRequest(domainFqn, username) = message
    reply(permissionsStore.getUserRolePermissions(username, domainFqn).map { roles => new PermissionsProfile(roles) })
  }

  def getAllUserRoles(message: GetAllUserRolesRequest): Unit = {
    val GetAllUserRolesRequest(domainFqn) = message
    reply(permissionsStore.getAllUserRoles(domainFqn))
  }

  def getUserRoles(message: GetUserRolesRequest): Unit = {
    val GetUserRolesRequest(username: String, domainFqn) = message
    reply(permissionsStore.getUserRoles(username, domainFqn))
  }

  def getUserPermissions(message: GetUserPermissionsRequest): Unit = {
    val GetUserPermissionsRequest(username: String, domainFqn) = message
    reply(permissionsStore.getAllUserPermissions(username, domainFqn))
  }
}
