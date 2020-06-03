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
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}

class UserGroupStoreActor private[datastore](private[this] val context: ActorContext[UserGroupStoreActor.Message],
                                             private[this] val groupStore: UserGroupStore)
  extends AbstractBehavior[UserGroupStoreActor.Message](context) with Logging {

  import UserGroupStoreActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case CreateUserGroupRequest(group, replyTo) =>
        groupStore.createUserGroup(group) match {
          case Success(_) =>
            replyTo ! RequestSuccess()
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }

      case DeleteUserGroupRequest(id, replyTo) =>
        groupStore.deleteUserGroup(id) match {
          case Success(_) =>
            replyTo ! RequestSuccess()
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }

      case GetUserGroupRequest(id, replyTo) =>
        groupStore.getUserGroup(id) match {
          case Success(group) =>
            replyTo ! GetUserGroupSuccess(group)
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }

      case GetUserGroupsRequest(filter, offset, limit, replyTo) =>
        groupStore.getUserGroups(filter, offset, limit) match {
          case Success(groups) =>
            replyTo ! GetUserGroupsSuccess(groups)
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }

      case GetUserGroupSummaryRequest(id, replyTo) =>
        groupStore.getUserGroupSummary(id) match {
          case Success(summary) =>
            replyTo ! GetUserGroupSummarySuccess(summary)
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }

      case GetUserGroupSummariesRequest(filter, offset, limit, replyTo) =>
        groupStore.getUserGroupSummaries(filter, offset, limit) match {
          case Success(summaries) =>
            replyTo ! GetUserGroupSummariesSuccess(summaries)
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }

      case GetUserGroupInfoRequest(id, replyTo) =>
        groupStore.getUserGroupInfo(id) match {
          case Success(info) =>
            replyTo ! GetUserGroupInfoSuccess(info)
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }

      case UpdateUserGroupRequest(id, group, replyTo) =>
        groupStore.updateUserGroup(id, group) match {
          case Success(_) =>
            replyTo ! RequestSuccess()
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }

      case UpdateUserGroupInfoRequest(id, info, replyTo) =>
        groupStore.updateUserGroupInfo(id, info) match {
          case Success(_) =>
            replyTo ! RequestSuccess()
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }

      case AddUserToGroupRequest(id, userId, replyTo) =>
        groupStore.addUserToGroup(id, userId) match {
          case Success(_) =>
            replyTo ! RequestSuccess()
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }

      case RemoveUserFromGroupRequest(id, userId, replyTo) =>
        groupStore.removeUserFromGroup(id, userId) match {
          case Success(_) =>
            replyTo ! RequestSuccess()
          case Failure(cause) =>
            replyTo ! RequestFailure(cause)
        }
    }

    Behaviors.same
  }
}


object UserGroupStoreActor {
  def apply(groupStore: UserGroupStore): Behavior[Message] = Behaviors.setup { context =>
    new UserGroupStoreActor(context, groupStore)
  }

  sealed trait Message extends CborSerializable with DomainRestMessageBody

  //
  // AddUserToGroup
  //
  case class AddUserToGroupRequest(groupId: String, userId: DomainUserId, replyTo: ActorRef[AddUserToGroupResponse]) extends Message

  sealed trait AddUserToGroupResponse extends CborSerializable


  //
  // RemoveUserFromGroup
  //
  case class RemoveUserFromGroupRequest(groupId: String, userId: DomainUserId, replyTo: ActorRef[RemoveUserFromGroupResponse]) extends Message

  sealed trait RemoveUserFromGroupResponse extends CborSerializable

  //
  // CreateUserGroup
  //
  case class CreateUserGroupRequest(group: UserGroup, replyTo: ActorRef[CreateUserGroupResponse]) extends Message

  sealed trait CreateUserGroupResponse extends CborSerializable


  //
  // UpdateUserGroup
  //
  case class UpdateUserGroupRequest(id: String, group: UserGroup, replyTo: ActorRef[UpdateUserGroupResponse]) extends Message

  sealed trait UpdateUserGroupResponse extends CborSerializable

  //
  // UpdateUserGroupInfo
  //
  case class UpdateUserGroupInfoRequest(id: String, group: UserGroupInfo, replyTo: ActorRef[UpdateUserGroupInfoResponse]) extends Message

  sealed trait UpdateUserGroupInfoResponse extends CborSerializable

  //
  // DeleteUserGroup
  //
  case class DeleteUserGroupRequest(id: String, replyTo: ActorRef[DeleteUserGroupResponse]) extends Message

  sealed trait DeleteUserGroupResponse extends CborSerializable

  //
  // GetUserGroup
  //
  case class GetUserGroupRequest(id: String, replyTo: ActorRef[GetUserGroupResponse]) extends Message

  sealed trait GetUserGroupResponse extends CborSerializable

  case class GetUserGroupSuccess(userGroup: Option[UserGroup]) extends GetUserGroupResponse

  //
  // GetUserGroupIng
  //
  case class GetUserGroupInfoRequest(id: String, replyTo: ActorRef[GetUserGroupInfoResponse]) extends Message

  sealed trait GetUserGroupInfoResponse extends CborSerializable

  case class GetUserGroupInfoSuccess(groupInfo: Option[UserGroupInfo]) extends GetUserGroupInfoResponse

  //
  // GetUserGroupSummary
  //
  case class GetUserGroupSummaryRequest(id: String, replyTo: ActorRef[GetUserGroupSummaryResponse]) extends Message

  sealed trait GetUserGroupSummaryResponse extends CborSerializable

  case class GetUserGroupSummarySuccess(summary: Option[UserGroupSummary]) extends GetUserGroupSummaryResponse

  //
  // GetUserGroups
  //
  case class GetUserGroupsRequest(filter: Option[String], offset: Option[Int], limit: Option[Int], replyTo: ActorRef[GetUserGroupsResponse]) extends Message

  sealed trait GetUserGroupsResponse extends CborSerializable

  case class GetUserGroupsSuccess(userGroups: List[UserGroup]) extends GetUserGroupsResponse

  //
  // GetUserGroupSummaries
  //
  case class GetUserGroupSummariesRequest(filter: Option[String], offset: Option[Int], limit: Option[Int], replyTo: ActorRef[GetUserGroupSummariesResponse]) extends Message

  sealed trait GetUserGroupSummariesResponse extends CborSerializable

  case class GetUserGroupSummariesSuccess(summaries: List[UserGroupSummary]) extends GetUserGroupSummariesResponse

  //
  // Helpers
  //
  case class RequestFailure(cause: Throwable) extends CborSerializable
    with AddUserToGroupResponse
    with RemoveUserFromGroupResponse
    with CreateUserGroupResponse
    with UpdateUserGroupResponse
    with UpdateUserGroupInfoResponse
    with DeleteUserGroupResponse
    with GetUserGroupResponse
    with GetUserGroupInfoResponse
    with GetUserGroupSummaryResponse
    with GetUserGroupsResponse
    with GetUserGroupSummariesResponse

  case class RequestSuccess() extends CborSerializable
    with AddUserToGroupResponse
    with RemoveUserFromGroupResponse
    with CreateUserGroupResponse
    with UpdateUserGroupResponse
    with UpdateUserGroupInfoResponse
    with DeleteUserGroupResponse

}
