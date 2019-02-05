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
  case class SetRolesRequest(username: String, target: PermissionTarget, roles: List[String])

  case class GetPermissionsProfileRequest(target: PermissionTarget, username: String)
  case class GetAllUserRolesRequest(target: PermissionTarget)
  case class GetUserRolesRequest(username: String, target: PermissionTarget)
  case class GetUserPermissionsRequest(username: String, target: PermissionTarget)
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
    val SetRolesRequest(username, target, roles) = message
    reply(permissionsStore.setUserRolesForTarget(username, target, roles))
  }

  def getPermissionsProfile(message: GetPermissionsProfileRequest): Unit = {
    val GetPermissionsProfileRequest(target, username) = message
    reply(permissionsStore.getUserRolesForTarget(username, target).map { roles => new PermissionsProfile(roles) })
  }

  def getAllUserRoles(message: GetAllUserRolesRequest): Unit = {
    val GetAllUserRolesRequest(target) = message
    reply(permissionsStore.getAllUserRolesForTarget(target))
  }

  def getUserRoles(message: GetUserRolesRequest): Unit = {
    val GetUserRolesRequest(username, target) = message
    reply(permissionsStore.getUserRolesForTarget(username, target))
  }

  def getUserPermissions(message: GetUserPermissionsRequest): Unit = {
    val GetUserPermissionsRequest(username, target) = message
    reply(permissionsStore.getUserPermissionsForTarget(username, target))
  }
}
