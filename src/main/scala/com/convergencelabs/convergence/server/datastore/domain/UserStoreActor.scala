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
import com.convergencelabs.convergence.server.datastore.domain.DomainUserStore.{CreateNormalDomainUser, UpdateDomainUser}
import com.convergencelabs.convergence.server.datastore.{SortOrder, StoreActor}
import com.convergencelabs.convergence.server.domain.{DomainUser, DomainUserId}

import scala.util.Success


class UserStoreActor private[datastore](private[this] val userStore: DomainUserStore)
  extends StoreActor with ActorLogging {

  import UserStoreActor._

  def receive: Receive = {
    case message: UserStoreRequest => onUserStoreRequest(message)
    case message: Any => unhandled(message)
  }

  def onUserStoreRequest(message: UserStoreRequest): Unit = {
    message match {
      case message: GetUserByUsernameRequest =>
        onGetUserByUsername(message)
      case message: CreateUserRequest =>
        onCreateUser(message)
      case message: DeleteDomainUserRequest =>
        onDeleteUser(message)
      case message: UpdateUserRequest =>
        onUpdateUser(message)
      case message: SetPasswordRequest =>
        onSetPassword(message)
      case message: GetUsersRequest =>
        onGetAllUsers(message)
      case message: FindUsersRequest =>
        onFindUser(message)
    }
  }

  private[this] def onGetAllUsers(message: GetUsersRequest): Unit = {
    val GetUsersRequest(filter, offset, limit) = message
    reply((filter match {
      case Some(filterString) =>
        userStore.searchUsersByFields(
          List(DomainUserField.Username, DomainUserField.Email),
          filterString,
          Some(DomainUserField.Username),
          Some(SortOrder.Ascending),
          offset,
          limit)
      case None =>
        userStore.getAllDomainUsers(Some(DomainUserField.Username), Some(SortOrder.Ascending), limit, offset)
    }).map(GetUsersResponse))
  }

  private[this] def onFindUser(message: FindUsersRequest): Unit = {
    val FindUsersRequest(search, exclude, limit, offset) = message
    reply(userStore.findUser(search, exclude.getOrElse(List()), offset.getOrElse(0), limit.getOrElse(10)).map(FindUsersResponse))
  }

  private[this] def onCreateUser(message: CreateUserRequest): Unit = {
    val CreateUserRequest(username, firstName, lastName, displayName, email, password) = message
    val domainUser = CreateNormalDomainUser(username, firstName, lastName, displayName, email)
    val result = userStore.createNormalDomainUser(domainUser) flatMap { createResult =>
      // FIXME this only works as a hack because of the way our create result works.
      password match {
        case None =>
          Success(createResult)
        case Some(pw) =>
          userStore.setDomainUserPassword(username, pw) map { _ =>
            createResult
          }
      }
    }
    reply(result.map(CreateUserResponse))
  }

  private[this] def onUpdateUser(message: UpdateUserRequest): Unit = {
    val UpdateUserRequest(username, firstName, lastName, displayName, email, disabled) = message
    val domainUser = UpdateDomainUser(DomainUserId.normal(username), firstName, lastName, displayName, email, disabled)
    reply(userStore.updateDomainUser(domainUser))
  }

  private[this] def onSetPassword(message: SetPasswordRequest): Unit = {
    val SetPasswordRequest(username, password) = message
    reply(userStore.setDomainUserPassword(username, password))
  }

  private[this] def onDeleteUser(message: DeleteDomainUserRequest): Unit = {
    reply(userStore.deleteNormalDomainUser(message.username))
  }

  private[this] def onGetUserByUsername(message: GetUserByUsernameRequest): Unit = {
    reply(userStore.getDomainUser(message.userId).map(GetUserByUsernameResponse))
  }
}


object UserStoreActor {
  def props(userStore: DomainUserStore): Props = Props(new UserStoreActor(userStore))

  trait UserStoreRequest extends CborSerializable

  case class GetUsersRequest(filter: Option[String],
                             offset: Option[Int],
                             limit: Option[Int]) extends UserStoreRequest

  case class GetUsersResponse(users: List[DomainUser]) extends CborSerializable

  case class GetUserByUsernameRequest(userId: DomainUserId) extends UserStoreRequest

  case class GetUserByUsernameResponse(user: Option[DomainUser]) extends CborSerializable

  case class CreateUserRequest(username: String,
                               firstName: Option[String],
                               lastName: Option[String],
                               displayName: Option[String],
                               email: Option[String],
                               password: Option[String]) extends UserStoreRequest

  case class CreateUserResponse(username: String) extends CborSerializable

  case class DeleteDomainUserRequest(username: String) extends UserStoreRequest

  case class UpdateUserRequest(username: String,
                               firstName: Option[String],
                               lastName: Option[String],
                               displayName: Option[String],
                               email: Option[String],
                               disabled: Option[Boolean]) extends UserStoreRequest

  case class SetPasswordRequest(uid: String,
                          password: String) extends UserStoreRequest

  case class FindUsersRequest(filter: String, exclude: Option[List[DomainUserId]], offset: Option[Int], limit: Option[Int]) extends UserStoreRequest

  case class FindUsersResponse(users: List[DomainUser])

}
