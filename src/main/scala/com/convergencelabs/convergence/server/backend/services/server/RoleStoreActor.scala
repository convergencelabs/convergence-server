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

package com.convergencelabs.convergence.server.backend.services.server

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.backend.datastore.convergence.RoleStore
import com.convergencelabs.convergence.server.backend.datastore.convergence.RoleStore.{Role, UserRoles}
import com.convergencelabs.convergence.server.backend.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.model.server.role.RoleTarget
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.JsonSubTypes

import scala.language.postfixOps

private final class RoleStoreActor(context: ActorContext[RoleStoreActor.Message],
                                   roleStore: RoleStore)
  extends AbstractBehavior[RoleStoreActor.Message](context) {

  import RoleStoreActor._

  context.system.receptionist ! Receptionist.Register(Key, context.self)

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
      case message: RemoveUserFromTargetRequest =>
        onRemoveUserRoleFromTarget(message)
    }

    Behaviors.same
  }

  private[this] def onCreateRole(message: CreateRoleRequest): Unit = {
    val CreateRoleRequest(role, replyTo) = message
    roleStore.createRole(role)
      .map(_ => CreateRoleResponse(Right(Ok())))
      .recover {
        case _: DuplicateValueException =>
          CreateRoleResponse(Left(RoleExistsError()))
        case cause =>
          context.log.error("unexpected error creating role", cause)
          CreateRoleResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onSetRolesRequest(message: SetUsersRolesForTargetRequest): Unit = {
    val SetUsersRolesForTargetRequest(username, target, roles, replyTo) = message
    roleStore
      .setUserRolesForTarget(username, target, roles)
      .map(_ => SetUsersRolesForTargetResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          SetUsersRolesForTargetResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("unexpected error setting roles for user and target", cause)
          SetUsersRolesForTargetResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onUpdateRolesForTarget(message: UpdateRolesForTargetRequest): Unit = {
    val UpdateRolesForTargetRequest(target, userRoles, replyTo) = message
    roleStore.setUserRolesForTarget(target, userRoles)
      .map(_ => UpdateRolesForTargetResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          UpdateRolesForTargetResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("unexpected error updating roles for target", cause)
          UpdateRolesForTargetResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onSetRolesForTarget(message: SetAllUserRolesForTargetRequest): Unit = {
    val SetAllUserRolesForTargetRequest(target, userRoles, replyTo) = message
    (for {
      _ <- roleStore.removeAllRolesFromTarget(target)
      _ <- roleStore.setUserRolesForTarget(target, userRoles)
    } yield {
      ()
    })
      .map(_ => SetAllUserRolesForTargetResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          SetAllUserRolesForTargetResponse(Left(TargetNotFoundError()))
        case cause =>
          context.log.error("unexpected error updating roles for target", cause)
          SetAllUserRolesForTargetResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetRoleProfile(message: GetRoleProfileRequest): Unit = {
    val GetRoleProfileRequest(target, username, replyTo) = message
    roleStore
      .getUserRolesForTarget(username, target)
      .map(roles => GetRoleProfileResponse(Right(roles)))
      .recover {
        case _: EntityNotFoundException =>
          GetRoleProfileResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("unexpected error getting role profile", cause)
          GetRoleProfileResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetAllUserRoles(message: GetAllUserRolesRequest): Unit = {
    val GetAllUserRolesRequest(target, replyTo) = message
    roleStore
      .getAllUserRolesForTarget(target)
      .map(roles => GetAllUserRolesResponse(Right(roles)))
      .recover {
        case _: EntityNotFoundException =>
          GetAllUserRolesResponse(Left(TargetNotFoundError()))
        case cause =>
          context.log.error("unexpected error getting roles for target", cause)
          GetAllUserRolesResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetUserRoles(message: GetUserRolesForTargetRequest): Unit = {
    val GetUserRolesForTargetRequest(username, target, replyTo) = message
    roleStore
      .getUserRolesForTarget(username, target)
      .map(roles => GetUserRolesForTargetResponse(Right(roles)))
      .recover {
        case _: EntityNotFoundException =>
          GetUserRolesForTargetResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("unexpected error getting roles for user and target", cause)
          GetUserRolesForTargetResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetUserPermissions(message: GetUserPermissionsRequest): Unit = {
    val GetUserPermissionsRequest(username, target, replyTo) = message
    roleStore
      .getUserPermissionsForTarget(username, target)
      .map(permissions => GetUserPermissionsResponse(Right(permissions)))
      .recover {
        case _: EntityNotFoundException =>
          GetUserPermissionsResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("unexpected error getting permissions for user and target", cause)
          GetUserPermissionsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onRemoveUserRoleFromTarget(message: RemoveUserFromTargetRequest): Unit = {
    val RemoveUserFromTargetRequest(target, username, replyTo) = message
    roleStore
      .removeUserRoleFromTarget(target, username)
      .map(_ => RemoveUserFromTargetResponse(Right(Ok())))
      .recover {
        case _: EntityNotFoundException =>
          RemoveUserFromTargetResponse(Left(UserNotFoundError()))
        case cause =>
          context.log.error("unexpected error removing user from target", cause)
          RemoveUserFromTargetResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }
}

object RoleStoreActor {
  val Key: ServiceKey[Message] = ServiceKey[Message]("RoleStore")

  def apply(roleStore: RoleStore): Behavior[Message] =
    Behaviors.setup(context => new RoleStoreActor(context, roleStore))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  trait Message extends CborSerializable

  //
  // CreateRole
  //
  final case class CreateRoleRequest(role: Role, replyTo: ActorRef[CreateRoleResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[RoleExistsError], name = "role_exists"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait CreateRoleError

  final case class RoleExistsError() extends CreateRoleError

  final case class CreateRoleResponse(response: Either[CreateRoleError, Ok]) extends CborSerializable

  //
  // SetUsersRolesForTarget
  //
  final case class SetUsersRolesForTargetRequest(username: String, target: RoleTarget, roles: Set[String], replyTo: ActorRef[SetUsersRolesForTargetResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetUsersRolesForTargetError

  final case class SetUsersRolesForTargetResponse(response: Either[SetUsersRolesForTargetError, Ok]) extends CborSerializable

  //
  // GetRoleProfile
  //
  final case class GetRoleProfileRequest(target: RoleTarget, username: String, replyTo: ActorRef[GetRoleProfileResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetRoleProfileError

  final case class GetRoleProfileResponse(profile: Either[GetRoleProfileError, Set[Role]]) extends CborSerializable

  //
  // GetAllUserRoles
  //
  final case class GetAllUserRolesRequest(target: RoleTarget, replyTo: ActorRef[GetAllUserRolesResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[TargetNotFoundError], name = "target_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetAllUserRolesForTargetError

  final case class GetAllUserRolesResponse(userRoles: Either[GetAllUserRolesForTargetError, Set[UserRoles]]) extends CborSerializable

  //
  // GetUserRolesForTarget
  //
  final case class GetUserRolesForTargetRequest(username: String,
                                                target: RoleTarget,
                                                replyTo: ActorRef[GetUserRolesForTargetResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserRolesForTargetError

  final case class GetUserRolesForTargetResponse(roles: Either[GetUserRolesForTargetError, Set[Role]]) extends CborSerializable

  //
  // UpdateRolesForTarget
  //
  final case class UpdateRolesForTargetRequest(target: RoleTarget, userRoles: Map[String, Set[String]], replyTo: ActorRef[UpdateRolesForTargetResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown"),
    new JsonSubTypes.Type(value = classOf[TargetNotFoundError], name = "target_not_found")
  ))
  sealed trait UpdateRolesForTargetError

  final case class UpdateRolesForTargetResponse(response: Either[UpdateRolesForTargetError, Ok]) extends CborSerializable

  //
  // SetAllUserRolesForTarget
  //
  final case class SetAllUserRolesForTargetRequest(target: RoleTarget, userRoles: Map[String, Set[String]], replyTo: ActorRef[SetAllUserRolesForTargetResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown"),
    new JsonSubTypes.Type(value = classOf[TargetNotFoundError], name = "target_not_found")
  ))
  sealed trait SetAllUserRolesForTargetError

  final case class SetAllUserRolesForTargetResponse(response: Either[SetAllUserRolesForTargetError, Ok]) extends CborSerializable

  //
  // RemoveUserFromTarget
  //
  final case class RemoveUserFromTargetRequest(target: RoleTarget,
                                               username: String,
                                               replyTo: ActorRef[RemoveUserFromTargetResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait RemoveUserFromTargetError

  final case class RemoveUserFromTargetResponse(response: Either[RemoveUserFromTargetError, Ok]) extends CborSerializable

  //
  // GetUserPermissions
  //
  final case class GetUserPermissionsRequest(username: String, target: RoleTarget, replyTo: ActorRef[GetUserPermissionsResponse]) extends Message

  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserPermissionsError

  final case class GetUserPermissionsResponse(permissions: Either[GetUserPermissionsError, Set[String]]) extends CborSerializable

  //
  // Commons Errors
  //
  final case class UserNotFoundError() extends AnyRef
    with SetUsersRolesForTargetError
    with GetRoleProfileError
    with SetAllUserRolesForTargetError
    with RemoveUserFromTargetError
    with GetUserPermissionsError
    with UpdateRolesForTargetError
    with GetUserRolesForTargetError

  final case class TargetNotFoundError() extends AnyRef
    with SetAllUserRolesForTargetError
    with GetAllUserRolesForTargetError
    with UpdateRolesForTargetError
    with RemoveUserFromTargetError

  final case class UnknownError() extends AnyRef
    with CreateRoleError
    with SetUsersRolesForTargetError
    with GetRoleProfileError
    with GetAllUserRolesForTargetError
    with GetUserRolesForTargetError
    with UpdateRolesForTargetError
    with SetAllUserRolesForTargetError
    with RemoveUserFromTargetError
    with GetUserPermissionsError

}
