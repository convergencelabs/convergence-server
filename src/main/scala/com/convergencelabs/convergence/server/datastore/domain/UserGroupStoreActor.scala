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


import akka.actor.{ActorLogging, Props}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.StoreActor
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody

class UserGroupStoreActor private[datastore](private[this] val groupStore: UserGroupStore)
  extends StoreActor with ActorLogging {

  import UserGroupStoreActor._

  def receive: Receive = {
    case message: UserGroupStoreRequest =>
      onUserGroupStoreRequest(message)
    case message: Any =>
      unhandled(message)
  }

  def onUserGroupStoreRequest(message: UserGroupStoreRequest): Unit = {
    message match {
      case CreateUserGroupRequest(group) =>
        reply(groupStore.createUserGroup(group))
      case DeleteUserGroupRequest(id) =>
        reply(groupStore.deleteUserGroup(id))

      case GetUserGroupRequest(id) =>
        reply(groupStore.getUserGroup(id).map(GetUserGroupResponse))
      case GetUserGroupsRequest(filter, offset, limit) =>
        reply(groupStore.getUserGroups(filter, offset, limit).map(GetUserGroupsResponse))
      case GetUserGroupSummaryRequest(id) =>
        reply(groupStore.getUserGroupSummary(id).map(GetUserGroupSummaryResponse))
      case GetUserGroupSummariesRequest(filter, offset, limit) =>
        reply(groupStore.getUserGroupSummaries(filter, offset, limit).map(GetUserGroupSummariesResponse))
      case GetUserGroupInfoRequest(id) =>
        reply(groupStore.getUserGroupInfo(id).map(GetUserGroupInfoResponse))

      case UpdateUserGroupRequest(id, group) =>
        reply(groupStore.updateUserGroup(id, group))
      case UpdateUserGroupInfoRequest(id, info) =>
        reply(groupStore.updateUserGroupInfo(id, info))

      case AddUserToGroupRequest(id, userId) =>
        reply(groupStore.addUserToGroup(id, userId))
      case RemoveUserFromGroupRequest(id, userId) =>
        reply(groupStore.removeUserFromGroup(id, userId))
    }
  }
}


object UserGroupStoreActor {
  def props(groupStore: UserGroupStore): Props = Props(new UserGroupStoreActor(groupStore))

  sealed trait UserGroupStoreRequest extends CborSerializable with DomainRestMessageBody

  case class AddUserToGroupRequest(groupId: String, userId: DomainUserId) extends UserGroupStoreRequest

  case class RemoveUserFromGroupRequest(groupId: String, userId: DomainUserId) extends UserGroupStoreRequest

  case class CreateUserGroupRequest(group: UserGroup) extends UserGroupStoreRequest

  case class UpdateUserGroupRequest(id: String, group: UserGroup) extends UserGroupStoreRequest

  case class UpdateUserGroupInfoRequest(id: String, group: UserGroupInfo) extends UserGroupStoreRequest

  case class DeleteUserGroupRequest(id: String) extends UserGroupStoreRequest

  case class GetUserGroupRequest(id: String) extends UserGroupStoreRequest

  case class GetUserGroupResponse(userGroup: Option[UserGroup]) extends CborSerializable

  case class GetUserGroupInfoRequest(id: String) extends UserGroupStoreRequest

  case class GetUserGroupInfoResponse(groupInfo: Option[UserGroupInfo]) extends CborSerializable

  case class GetUserGroupSummaryRequest(id: String) extends UserGroupStoreRequest

  case class GetUserGroupSummaryResponse(summary: Option[UserGroupSummary]) extends CborSerializable

  case class GetUserGroupsRequest(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends UserGroupStoreRequest

  case class GetUserGroupsResponse(userGroups: List[UserGroup]) extends CborSerializable

  case class GetUserGroupSummariesRequest(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends UserGroupStoreRequest

  case class GetUserGroupSummariesResponse(summaries: List[UserGroupSummary]) extends CborSerializable

}
