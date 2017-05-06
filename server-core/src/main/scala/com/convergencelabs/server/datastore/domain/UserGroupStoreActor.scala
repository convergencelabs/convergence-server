package com.convergencelabs.server.datastore

import akka.actor.ActorLogging
import akka.actor.Props
import scala.util.Success
import com.convergencelabs.server.domain.model.query.Ast.OrderBy
import com.convergencelabs.server.datastore.domain.UserGroup
import com.convergencelabs.server.datastore.domain.UserGroupStore

object UserGroupStoreActor {
  def props(groupStore: UserGroupStore): Props = Props(new UserGroupStoreActor(groupStore))

  sealed trait UserGroupStoreRequest
  case class CreateUserGroup(group: UserGroup)
  case class UpdateUserGroup(id: String, group: UserGroup)
  case class DeleteUserGroup(id: String)
  case class GetUserGroup(id: String)
  case class GetUserGroups(filter: Option[String], offset: Option[Int], limit: Option[Int])
}

class UserGroupStoreActor private[datastore] (private[this] val groupStore: UserGroupStore)
    extends StoreActor with ActorLogging {

  import UserGroupStoreActor._

  def receive: Receive = {
    case message: CreateUserGroup   => createUserGroup(message)
    case message: UpdateUserGroup   => updateUserGroup(message)
    case message: DeleteUserGroup   => deleteUserGroup(message)
    case message: GetUserGroup      => getUserGroup(message)
    case message: GetUserGroups     => getUserGroups(message)
    case message: Any               => unhandled(message)
  }

  def createUserGroup(message: CreateUserGroup): Unit = {
    val CreateUserGroup(group) = message
    reply(groupStore.createUserGroup(group))
  }

  def updateUserGroup(message: UpdateUserGroup): Unit = {
    val UpdateUserGroup(id, group) = message
    reply(groupStore.updateUserGroup(id, group))
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
}
