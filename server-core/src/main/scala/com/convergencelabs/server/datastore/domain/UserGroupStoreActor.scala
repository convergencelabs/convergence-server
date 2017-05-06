package com.convergencelabs.server.datastore

import akka.actor.ActorLogging
import akka.actor.Props
import scala.util.Success
import com.convergencelabs.server.domain.model.query.Ast.OrderBy
import com.convergencelabs.server.datastore.domain.UserGroup
import com.convergencelabs.server.datastore.domain.UserGroupStore
import com.convergencelabs.server.datastore.domain.UserGroupInfo

object UserGroupStoreActor {
  def props(groupStore: UserGroupStore): Props = Props(new UserGroupStoreActor(groupStore))

  sealed trait UserGroupStoreRequest
  case class AddUserToGroup(groupId: String, username: String) extends UserGroupStoreRequest
  case class RemoveUserFromGroup(groupId: String, username: String) extends UserGroupStoreRequest
  case class CreateUserGroup(group: UserGroup) extends UserGroupStoreRequest
  case class UpdateUserGroup(id: String, group: UserGroup) extends UserGroupStoreRequest
  case class UpdateUserGroupInfo(id: String, group: UserGroupInfo) extends UserGroupStoreRequest
  case class DeleteUserGroup(id: String) extends UserGroupStoreRequest
  case class GetUserGroup(id: String) extends UserGroupStoreRequest
  case class GetUserGroupSummary(id: String) extends UserGroupStoreRequest
  case class GetUserGroups(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends UserGroupStoreRequest
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
      case message: CreateUserGroup => createUserGroup(message)
      case message: UpdateUserGroup => updateUserGroup(message)
      case message: UpdateUserGroupInfo => updateUserGroupInfo(message)
      case message: AddUserToGroup => addUserToGroup(message)
      case message: RemoveUserFromGroup => removeUserFromGroup(message)
      case message: DeleteUserGroup => deleteUserGroup(message)
      case message: GetUserGroup => getUserGroup(message)
      case message: GetUserGroups => getUserGroups(message)
      case message: GetUserGroupSummary => getUserGroupSummary(message)
    }
  }

  def createUserGroup(message: CreateUserGroup): Unit = {
    val CreateUserGroup(group) = message
    reply(groupStore.createUserGroup(group))
  }

  def updateUserGroup(message: UpdateUserGroup): Unit = {
    val UpdateUserGroup(id, group) = message
    reply(groupStore.updateUserGroup(id, group))
  }
  
  def updateUserGroupInfo(message: UpdateUserGroupInfo): Unit = {
    val UpdateUserGroupInfo(id, description) = message
    reply(groupStore.updateUserGroupInfo(id, description))
  }

  def deleteUserGroup(message: DeleteUserGroup): Unit = {
    val DeleteUserGroup(id) = message
    reply(groupStore.deleteUserGroup(id))
  }

  def getUserGroup(message: GetUserGroup): Unit = {
    val GetUserGroup(id) = message
    reply(groupStore.getUserGroup(id))
  }

  def getUserGroups(message: GetUserGroups): Unit = {
    val GetUserGroups(filter, offset, limit) = message
    reply(groupStore.getUserGroups(filter, offset, limit))
  }
  
  def getUserGroupSummary(message: GetUserGroupSummary): Unit = {
    val GetUserGroupSummary(id) = message
    reply(groupStore.getUserGroupSummary(id))
  }
  
  def addUserToGroup(message: AddUserToGroup): Unit = {
    val AddUserToGroup(id, username) = message
    reply(groupStore.addUserToGroup(id, username))
  }
  
  def removeUserFromGroup(message: RemoveUserFromGroup): Unit = {
    val RemoveUserFromGroup(id, username) = message
    reply(groupStore.removeUserFromGroup(id, username))
  }
}
