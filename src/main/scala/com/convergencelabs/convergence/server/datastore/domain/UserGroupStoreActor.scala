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

package com.convergencelabs.convergence.server.datastore.domain


import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.{DuplicateValueException, EntityNotFoundException}
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

class UserGroupStoreActor private(context: ActorContext[UserGroupStoreActor.Message],
                                  groupStore: UserGroupStore)
  extends AbstractBehavior[UserGroupStoreActor.Message](context) {

  import UserGroupStoreActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case CreateUserGroupRequest(group, replyTo) =>
        groupStore
          .createUserGroup(group)
          .map(_ => Right(()))
          .recover {
            case _: DuplicateValueException =>
              Left(GroupAlreadyExistsError())
            case cause =>
              context.log.error("unexpected error deleting group", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! CreateUserGroupResponse(_))

      case DeleteUserGroupRequest(id, replyTo) =>
        groupStore
          .deleteUserGroup(id)
          .map(_ => Right(()))
          .recover {
            case _: EntityNotFoundException =>
              Left(GroupNotFoundError())
            case cause =>
              context.log.error("unexpected error deleting group", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! DeleteUserGroupResponse(_))

      case GetUserGroupRequest(id, replyTo) =>
        groupStore
          .getUserGroup(id)
          .map(_.map(Right(_)).getOrElse(Left(GroupNotFoundError())))
          .recover {
            case cause =>
              context.log.error("unexpected error getting group", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! GetUserGroupResponse(_))

      case GetUserGroupsRequest(filter, offset, limit, replyTo) =>
        groupStore
          .getUserGroups(filter, offset, limit)
          .map(Right(_))
          .recover {
            case cause =>
              context.log.error("unexpected error getting groups", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! GetUserGroupsResponse(_))

      case GetUserGroupSummaryRequest(id, replyTo) =>
        groupStore
          .getUserGroupSummary(id)
          .map(_.map(Right(_)).getOrElse(Left(GroupNotFoundError())))
          .recover {
            case cause =>
              context.log.error("unexpected error getting group summary", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! GetUserGroupSummaryResponse(_))

      case GetUserGroupSummariesRequest(filter, offset, limit, replyTo) =>
        groupStore
          .getUserGroupSummaries(filter, offset, limit)
          .map(Right(_))
          .recover {
            case cause =>
              context.log.error("unexpected error getting group info", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! GetUserGroupSummariesResponse(_))

      case GetUserGroupInfoRequest(id, replyTo) =>
        groupStore
          .getUserGroupInfo(id)
          .map(_.map(Right(_)).getOrElse(Left(GroupNotFoundError())))
          .recover {
            case cause =>
              context.log.error("unexpected error getting group info", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! GetUserGroupInfoResponse(_))

      case UpdateUserGroupRequest(id, group, replyTo) =>
        groupStore
          .updateUserGroup(id, group)
          .map(_ => Right(()))
          .recover {
            case _: EntityNotFoundException =>
              Left(GroupNotFoundError())
            case cause =>
              context.log.error("unexpected error updating group info", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! UpdateUserGroupResponse(_))

      case UpdateUserGroupInfoRequest(id, info, replyTo) =>
        groupStore
          .updateUserGroupInfo(id, info)
          .map(_ => Right(()))
          .recover {
            case _: EntityNotFoundException =>
              Left(GroupNotFoundError())
            case cause =>
              context.log.error("unexpected error updating group info", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! UpdateUserGroupInfoResponse(_))

      case AddUserToGroupRequest(id, userId, replyTo) =>
        groupStore
          .addUserToGroup(id, userId)
          .map(_ => Right(()))
          .recover {
            case _: EntityNotFoundException =>
              Left(UserNotFoundError())
            case cause =>
              context.log.error("unexpected error adding user to group", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! AddUserToGroupResponse(_))

      case RemoveUserFromGroupRequest(id, userId, replyTo) =>
        groupStore
          .removeUserFromGroup(id, userId)
          .map(_ => Right(()))
          .recover {
            case _: EntityNotFoundException =>
              Left(UserNotFoundError())
            case cause =>
              context.log.error("unexpected error removing user from group", cause)
              Left(UnknownError())
          }
          .foreach(replyTo ! RemoveUserFromGroupResponse(_))
    }

    Behaviors.same
  }
}


object UserGroupStoreActor {
  def apply(groupStore: UserGroupStore): Behavior[Message] =
    Behaviors.setup(context => new UserGroupStoreActor(context, groupStore))


  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable with DomainRestMessageBody

  //
  // AddUserToGroup
  //
  final case class AddUserToGroupRequest(groupId: String, userId: DomainUserId, replyTo: ActorRef[AddUserToGroupResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[GroupNotFoundError], name = "group_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait AddUserToGroupError

  final case class AddUserToGroupResponse(response: Either[AddUserToGroupError, Unit]) extends CborSerializable


  //
  // RemoveUserFromGroup
  //
  final case class RemoveUserFromGroupRequest(groupId: String, userId: DomainUserId, replyTo: ActorRef[RemoveUserFromGroupResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFoundError], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[GroupNotFoundError], name = "group_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait RemoveUserFromGroupError

  final case class RemoveUserFromGroupResponse(response: Either[RemoveUserFromGroupError, Unit]) extends CborSerializable

  //
  // CreateUserGroup
  //
  final case class CreateUserGroupRequest(group: UserGroup, replyTo: ActorRef[CreateUserGroupResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GroupAlreadyExistsError], name = "group_exists"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait CreateUserGroupError

  final case class GroupAlreadyExistsError() extends CreateUserGroupError

  final case class CreateUserGroupResponse(response: Either[CreateUserGroupError, Unit]) extends CborSerializable


  //
  // UpdateUserGroup
  //
  final case class UpdateUserGroupRequest(id: String, group: UserGroup, replyTo: ActorRef[UpdateUserGroupResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GroupNotFoundError], name = "group_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait UpdateUserGroupError

  final case class UpdateUserGroupResponse(response: Either[UpdateUserGroupError, Unit]) extends CborSerializable

  //
  // UpdateUserGroupInfo
  //
  final case class UpdateUserGroupInfoRequest(id: String, group: UserGroupInfo, replyTo: ActorRef[UpdateUserGroupInfoResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GroupNotFoundError], name = "group_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait UpdateUserGroupInfoError

  final case class UpdateUserGroupInfoResponse(response: Either[UpdateUserGroupInfoError, Unit]) extends CborSerializable

  //
  // DeleteUserGroup
  //
  final case class DeleteUserGroupRequest(id: String, replyTo: ActorRef[DeleteUserGroupResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GroupNotFoundError], name = "group_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait DeleteUserGroupError

  final case class DeleteUserGroupResponse(response: Either[DeleteUserGroupError, Unit]) extends CborSerializable

  //
  // GetUserGroup
  //
  final case class GetUserGroupRequest(id: String, replyTo: ActorRef[GetUserGroupResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GroupNotFoundError], name = "group_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserGroupError

  final case class GetUserGroupResponse(userGroup: Either[GetUserGroupError, UserGroup]) extends CborSerializable

  //
  // GetUserGroupIng
  //
  final case class GetUserGroupInfoRequest(id: String, replyTo: ActorRef[GetUserGroupInfoResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GroupNotFoundError], name = "group_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserGroupInfoError

  final case class GetUserGroupInfoResponse(groupInfo: Either[GetUserGroupInfoError, UserGroupInfo]) extends CborSerializable

  //
  // GetUserGroupSummary
  //
  final case class GetUserGroupSummaryRequest(id: String, replyTo: ActorRef[GetUserGroupSummaryResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GroupNotFoundError], name = "group_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserGroupSummaryError

  final case class GetUserGroupSummaryResponse(summary: Either[GetUserGroupSummaryError, UserGroupSummary]) extends CborSerializable

  //
  // GetUserGroups
  //
  final case class GetUserGroupsRequest(filter: Option[String], offset: Option[Int], limit: Option[Int], replyTo: ActorRef[GetUserGroupsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserGroupsError

  final case class GetUserGroupsResponse(userGroups: Either[GetUserGroupsError, List[UserGroup]]) extends CborSerializable

  //
  // GetUserGroupSummaries
  //
  final case class GetUserGroupSummariesRequest(filter: Option[String],
                                          offset: Option[Int],
                                          limit: Option[Int],
                                          replyTo: ActorRef[GetUserGroupSummariesResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserGroupSummariesError

  final case class GetUserGroupSummariesResponse(summaries: Either[GetUserGroupSummariesError, List[UserGroupSummary]]) extends CborSerializable

  //
  // Common Errors
  //
  final case class UserNotFoundError() extends AnyRef
    with AddUserToGroupError
    with RemoveUserFromGroupError

  final case class GroupNotFoundError() extends AnyRef
    with AddUserToGroupError
    with RemoveUserFromGroupError
    with UpdateUserGroupError
    with UpdateUserGroupInfoError
    with DeleteUserGroupError
    with GetUserGroupError
    with GetUserGroupInfoError
    with GetUserGroupSummaryError

  final case class UnknownError() extends AnyRef
    with AddUserToGroupError
    with RemoveUserFromGroupError
    with CreateUserGroupError
    with UpdateUserGroupError
    with UpdateUserGroupInfoError
    with DeleteUserGroupError
    with GetUserGroupError
    with GetUserGroupInfoError
    with GetUserGroupSummaryError
    with GetUserGroupsError
    with GetUserGroupSummariesError

}
