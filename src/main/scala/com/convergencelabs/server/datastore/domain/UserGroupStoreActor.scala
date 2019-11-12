/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain


import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.domain.DomainUserId

import akka.actor.ActorLogging
import akka.actor.Props

object UserGroupStoreActor {
  def props(groupStore: UserGroupStore): Props = Props(new UserGroupStoreActor(groupStore))

  sealed trait UserGroupStoreRequest
  case class AddUserToGroup(groupId: String, userId: DomainUserId) extends UserGroupStoreRequest
  case class RemoveUserFromGroup(groupId: String, userId: DomainUserId) extends UserGroupStoreRequest
  case class CreateUserGroup(group: UserGroup) extends UserGroupStoreRequest
  case class UpdateUserGroup(id: String, group: UserGroup) extends UserGroupStoreRequest
  case class UpdateUserGroupInfo(id: String, group: UserGroupInfo) extends UserGroupStoreRequest
  case class DeleteUserGroup(id: String) extends UserGroupStoreRequest
  case class GetUserGroup(id: String) extends UserGroupStoreRequest
  case class GetUserGroupInfo(id: String) extends UserGroupStoreRequest
  case class GetUserGroupSummary(id: String) extends UserGroupStoreRequest
  case class GetUserGroups(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends UserGroupStoreRequest
  case class GetUserGroupSummaries(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends UserGroupStoreRequest
}

class UserGroupStoreActor private[datastore] (private[this] val groupStore: UserGroupStore)
    extends StoreActor with ActorLogging {

  import UserGroupStoreActor._

  def receive: Receive = {
    case message: UserGroupStoreRequest => onUserGroupStoreRequest(message)
    case message: Any => unhandled(message)
  }

  def onUserGroupStoreRequest(message: UserGroupStoreRequest): Unit = {
    message match {
      case CreateUserGroup(group) =>
        reply(groupStore.createUserGroup(group))
      case DeleteUserGroup(id) =>
        reply(groupStore.deleteUserGroup(id))
      
      case GetUserGroup(id) =>
        reply(groupStore.getUserGroup(id))
      case GetUserGroups(filter, offset, limit) =>
        reply(groupStore.getUserGroups(filter, offset, limit))
      case GetUserGroupSummary(id) =>
        reply(groupStore.getUserGroupSummary(id))
      case GetUserGroupSummaries(filter, offset, limit) =>
        reply(groupStore.getUserGroupSummaries(filter, offset, limit))
      case GetUserGroupInfo(id) =>
        reply(groupStore.getUserGroupInfo(id))
      
      case UpdateUserGroup(id, group) =>
        reply(groupStore.updateUserGroup(id, group))
      case UpdateUserGroupInfo(id, info) =>
        reply(groupStore.updateUserGroupInfo(id, info))
        
      case AddUserToGroup(id, userId) =>
        reply(groupStore.addUserToGroup(id, userId))
      case RemoveUserFromGroup(id, userId) =>
        reply(groupStore.removeUserFromGroup(id, userId))
    }
  }
}
