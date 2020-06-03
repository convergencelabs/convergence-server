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

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.convergence.RoleStore.{Role, UserRoles}
import com.convergencelabs.convergence.server.db.DatabaseProvider
import grizzled.slf4j.Logging

import scala.language.postfixOps
import scala.util.{Failure, Success}

private class RoleStoreActor(private[this] val context: ActorContext[RoleStoreActor.Message],
                             private[this] val dbProvider: DatabaseProvider)
  extends AbstractBehavior[RoleStoreActor.Message](context) with Logging {

  import RoleStoreActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

  private[this] val permissionsStore: RoleStore = new RoleStore(dbProvider)


  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
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
      case message: RemoveUserFromRequest =>
        onRemoveUserRoleFromTarget(message)
    }

    Behaviors.same
  }

  private[this] def onCreateRole(message: CreateRoleRequest): Unit = {
    val CreateRoleRequest(role, replyTo) = message
    permissionsStore.createRole(role) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onSetRolesRequest(message: SetUsersRolesForTargetRequest): Unit = {
    val SetUsersRolesForTargetRequest(username, target, roles, replyTo) = message
    permissionsStore.setUserRolesForTarget(username, target, roles) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onUpdateRolesForTarget(message: UpdateRolesForTargetRequest): Unit = {
    val UpdateRolesForTargetRequest(target, userRoles, replyTo) = message
    permissionsStore.setUserRolesForTarget(target, userRoles) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onSetRolesForTarget(message: SetAllUserRolesForTargetRequest): Unit = {
    val SetAllUserRolesForTargetRequest(target, userRoles, replyTo) = message
    (for {
      _ <- permissionsStore.removeAllRolesFromTarget(target)
      _ <- permissionsStore.setUserRolesForTarget(target, userRoles)
    } yield {
      ()
    }) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetRoleProfile(message: GetRoleProfileRequest): Unit = {
    val GetRoleProfileRequest(target, username, replyTo) = message
    permissionsStore.getUserRolesForTarget(username, target) match {
      case Success(roleProfile) =>
        replyTo ! GetRoleProfileSuccess(roleProfile)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetAllUserRoles(message: GetAllUserRolesRequest): Unit = {
    val GetAllUserRolesRequest(target, replyTo) = message
    permissionsStore.getAllUserRolesForTarget(target) match {
      case Success(roles) =>
        replyTo ! GetAllUserRolesSuccess(roles)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetUserRoles(message: GetUserRolesForTargetRequest): Unit = {
    val GetUserRolesForTargetRequest(username, target, replyTo) = message
    permissionsStore.getUserRolesForTarget(username, target) match {
      case Success(roles) =>
        replyTo ! GetUserRolesForTargetSuccess(roles)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetUserPermissions(message: GetUserPermissionsRequest): Unit = {
    val GetUserPermissionsRequest(username, target, replyTo) = message
    permissionsStore.getUserPermissionsForTarget(username, target) match {
      case Success(permissions) =>
        replyTo ! GetUserPermissionsSuccess(permissions)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onRemoveUserRoleFromTarget(message: RemoveUserFromRequest): Unit = {
    val RemoveUserFromRequest(target, username, replyTo) = message
    permissionsStore.removeUserRoleFromTarget(target, username) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}


object RoleStoreActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("RoleStore")

  def apply(dbProvider: DatabaseProvider): Behavior[Message] = Behaviors.setup(context => new RoleStoreActor(context, dbProvider))

  trait Message extends CborSerializable

  //
  // CreateRoleRequest
  //
  case class CreateRoleRequest(role: Role, replyTo: ActorRef[CreateRoleResponse]) extends Message

  sealed trait CreateRoleResponse extends CborSerializable

  //
  // SetUsersRolesForTargetRequest
  //
  case class SetUsersRolesForTargetRequest(username: String, target: RoleTarget, roles: Set[String], replyTo: ActorRef[SetUsersRolesForTargetResponse]) extends Message

  sealed trait SetUsersRolesForTargetResponse extends CborSerializable

  //
  // GetRoleProfileRequest
  //
  case class GetRoleProfileRequest(target: RoleTarget, username: String, replyTo: ActorRef[GetRoleProfileResponse]) extends Message

  sealed trait GetRoleProfileResponse extends CborSerializable

  case class GetRoleProfileSuccess(profile: Set[Role]) extends GetRoleProfileResponse

  //
  // GetAllUserRolesRequest
  //
  case class GetAllUserRolesRequest(target: RoleTarget, replyTo: ActorRef[GetAllUserRolesResponse]) extends Message

  sealed trait GetAllUserRolesResponse extends CborSerializable

  case class GetAllUserRolesSuccess(userRoles: Set[UserRoles]) extends GetAllUserRolesResponse

  //
  // GetUserRolesForTargetRequest
  //
  case class GetUserRolesForTargetRequest(username: String, target: RoleTarget, replyTo: ActorRef[GetUserRolesForTargetResponse]) extends Message

  sealed trait GetUserRolesForTargetResponse extends CborSerializable

  case class GetUserRolesForTargetSuccess(roles: Set[Role]) extends GetUserRolesForTargetResponse

  //
  // UpdateRolesForTarget
  //
  case class UpdateRolesForTargetRequest(target: RoleTarget, userRoles: Map[String, Set[String]], replyTo: ActorRef[UpdateRolesForTargetResponse]) extends Message

  sealed trait UpdateRolesForTargetResponse extends CborSerializable

  //
  // SetAllUserRolesForTarget
  //
  case class SetAllUserRolesForTargetRequest(target: RoleTarget, userRoles: Map[String, Set[String]], replyTo: ActorRef[SetAllUserRolesForTargetResponse]) extends Message

  sealed trait SetAllUserRolesForTargetResponse extends CborSerializable

  //
  // RemoveUserFromTarget
  //
  case class RemoveUserFromRequest(target: RoleTarget, username: String, replyTo: ActorRef[RemoveUserFromResponse]) extends Message

  sealed trait RemoveUserFromResponse extends CborSerializable

  //
  // GetUserPermissionsRequest
  //
  case class GetUserPermissionsRequest(username: String, target: RoleTarget, replyTo: ActorRef[GetUserPermissionsResponse]) extends Message

  sealed trait GetUserPermissionsResponse extends CborSerializable

  case class GetUserPermissionsSuccess(permissions: Set[String]) extends GetUserPermissionsResponse


  //
  // Generic Responses
  //
  case class RequestFailure(cause: Throwable) extends CborSerializable
    with CreateRoleResponse
    with SetUsersRolesForTargetResponse
    with GetRoleProfileResponse
    with GetAllUserRolesResponse
    with GetUserRolesForTargetResponse
    with UpdateRolesForTargetResponse
    with SetAllUserRolesForTargetResponse
    with RemoveUserFromResponse
    with GetUserPermissionsResponse


  case class RequestSuccess() extends CborSerializable
    with CreateRoleResponse
    with SetUsersRolesForTargetResponse
    with UpdateRolesForTargetResponse
    with SetAllUserRolesForTargetResponse
    with RemoveUserFromResponse

}
