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

package com.convergencelabs.convergence.server.datastore.convergence

import akka.actor.{ActorLogging, Props}
import akka.util.Timeout
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.datastore.convergence.RoleStore.{Role, UserRoles}
import com.convergencelabs.convergence.server.db.DatabaseProvider

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class RoleStoreActor private[datastore](private[this] val dbProvider: DatabaseProvider) extends StoreActor
  with ActorLogging {

  import RoleStoreActor._

  // FIXME: Read this from configuration
  private[this] implicit val requestTimeout: Timeout = Timeout(2 seconds)
  private[this] implicit val executionContext: ExecutionContextExecutor = context.dispatcher

  private[this] val permissionsStore: RoleStore = new RoleStore(dbProvider)

  def receive: Receive = {
    case message: CreateRoleRequest =>
      onCreateRole(message)
    case message: SetUsersRolesForTargetRequest =>
      onSetRolesRequest(message)
    case message: GetRoleProfileRequest =>
      onGetRoleProfile(message)
    case message: GetAllUserRolesRequest =>
      onGetAllUserRoles(message)
    case message: GetUserRolesForTargetRequest =>
      onGetUserRoles(message)
    case message: GetUserPermissionsRequest =>
      onGetUserPermissions(message)
    case message: UpdateRolesForTargetRequest =>
      onUpdateRolesForTarget(message)
    case message: SetAllUserRolesForTargetRequest =>
      onSetRolesForTarget(message)
    case message: RemoveUserFromTarget =>
      onRemoveUserRoleFromTarget(message)
    case message: Any =>
      unhandled(message)
  }

  private[this] def onCreateRole(message: CreateRoleRequest): Unit = {
    val CreateRoleRequest(role) = message
    reply(permissionsStore.createRole(role))
  }

  private[this] def onSetRolesRequest(message: SetUsersRolesForTargetRequest): Unit = {
    val SetUsersRolesForTargetRequest(username, target, roles) = message
    reply(permissionsStore.setUserRolesForTarget(username, target, roles))
  }

  private[this] def onUpdateRolesForTarget(message: UpdateRolesForTargetRequest): Unit = {
    val UpdateRolesForTargetRequest(target, userRoles) = message
    reply(permissionsStore.setUserRolesForTarget(target, userRoles))
  }

  private[this] def onSetRolesForTarget(message: SetAllUserRolesForTargetRequest): Unit = {
    val SetAllUserRolesForTargetRequest(target, userRoles) = message
    reply(for {
      _ <- permissionsStore.removeAllRolesFromTarget(target)
      _ <- permissionsStore.setUserRolesForTarget(target, userRoles)
    } yield {
      ()
    })
  }

  private[this] def onGetRoleProfile(message: GetRoleProfileRequest): Unit = {
    val GetRoleProfileRequest(target, username) = message
    reply(permissionsStore.getUserRolesForTarget(username, target)
      .map(GetRoleProfileResponse)
    )
  }

  private[this] def onGetAllUserRoles(message: GetAllUserRolesRequest): Unit = {
    val GetAllUserRolesRequest(target) = message
    reply(permissionsStore.getAllUserRolesForTarget(target).map(GetAllUserRolesResponse))
  }

  private[this] def onGetUserRoles(message: GetUserRolesForTargetRequest): Unit = {
    val GetUserRolesForTargetRequest(username, target) = message
    reply(permissionsStore.getUserRolesForTarget(username, target).map(GetUserRolesForTargetResponse))
  }

  private[this] def onGetUserPermissions(message: GetUserPermissionsRequest): Unit = {
    val GetUserPermissionsRequest(username, target) = message
    reply(permissionsStore.getUserPermissionsForTarget(username, target).map(GetUserPermissionsResponse))
  }

  private[this] def onRemoveUserRoleFromTarget(message: RemoveUserFromTarget): Unit = {
    val RemoveUserFromTarget(target, username) = message
    reply(permissionsStore.removeUserRoleFromTarget(target, username))
  }
}


object RoleStoreActor {
  val RelativePath = "RoleStoreActor"

  def props(dbProvider: DatabaseProvider): Props = Props(new RoleStoreActor(dbProvider))

  trait RoleStoreActorMessages extends CborSerializable

  case class CreateRoleRequest(role: Role) extends RoleStoreActorMessages

  case class SetUsersRolesForTargetRequest(username: String, target: RoleTarget, roles: Set[String]) extends RoleStoreActorMessages

  case class GetRoleProfileRequest(target: RoleTarget, username: String) extends RoleStoreActorMessages

  case class GetRoleProfileResponse(profile: Set[Role]) extends CborSerializable

  case class GetAllUserRolesRequest(target: RoleTarget) extends RoleStoreActorMessages

  case class GetAllUserRolesResponse(userRoles: Set[UserRoles]) extends CborSerializable

  case class GetUserRolesForTargetRequest(username: String, target: RoleTarget) extends RoleStoreActorMessages

  case class GetUserRolesForTargetResponse(roles: Set[Role]) extends CborSerializable

  case class UpdateRolesForTargetRequest(target: RoleTarget, userRoles: Map[String, Set[String]]) extends RoleStoreActorMessages

  case class SetAllUserRolesForTargetRequest(target: RoleTarget, userRoles: Map[String, Set[String]]) extends RoleStoreActorMessages

  case class RemoveUserFromTarget(target: RoleTarget, username: String) extends RoleStoreActorMessages

  case class GetUserPermissionsRequest(username: String, target: RoleTarget) extends RoleStoreActorMessages

  case class GetUserPermissionsResponse(permissions: Set[String]) extends CborSerializable

}
