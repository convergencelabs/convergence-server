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
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.SortOrder
import com.convergencelabs.convergence.server.datastore.domain.DomainUserStore.{CreateNormalDomainUser, UpdateDomainUser}
import com.convergencelabs.convergence.server.domain.rest.DomainRestActor.DomainRestMessageBody
import com.convergencelabs.convergence.server.domain.{DomainUser, DomainUserId}
import grizzled.slf4j.Logging

import scala.util.{Failure, Success}


class UserStoreActor private[datastore](private[this] val context: ActorContext[UserStoreActor.Message],
                                        private[this] val userStore: DomainUserStore)
  extends AbstractBehavior[UserStoreActor.Message](context) with Logging {

  import UserStoreActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case message: GetUserRequest =>
        onGetUser(message)
      case message: CreateUserRequest =>
        onCreateUser(message)
      case message: DeleteUserRequest =>
        onDeleteUser(message)
      case message: UpdateUserRequest =>
        onUpdateUser(message)
      case message: SetPasswordRequest =>
        onSetPassword(message)
      case message: GetUsersRequest =>
        onGetUsers(message)
      case message: FindUsersRequest =>
        onFindUser(message)
    }

    Behaviors.same
  }


  private[this] def onGetUsers(message: GetUsersRequest): Unit = {
    val GetUsersRequest(filter, offset, limit, replyTo) = message
    (filter match {
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
    }) match {
      case Success(users) =>
        replyTo ! GetUsersSuccess(users)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onGetUser(message: GetUserRequest): Unit = {
    val GetUserRequest(userId, replyTo) = message
    userStore.getDomainUser(userId) match {
      case Success(user) =>
        replyTo ! GetUserSuccess(user)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onFindUser(message: FindUsersRequest): Unit = {
    val FindUsersRequest(search, exclude, limit, offset, replyTo) = message
    userStore.findUser(search, exclude.getOrElse(List()), offset.getOrElse(0), limit.getOrElse(10)) match {
      case Success(users) =>
        replyTo ! FindUsersSuccess(users)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onCreateUser(message: CreateUserRequest): Unit = {
    val CreateUserRequest(username, firstName, lastName, displayName, email, password, replyTo) = message
    val domainUser = CreateNormalDomainUser(username, firstName, lastName, displayName, email)
    userStore.createNormalDomainUser(domainUser) flatMap { createResult =>
      // FIXME this only works as a hack because of the way our create result works.
      password match {
        case None =>
          Success(createResult)
        case Some(pw) =>
          userStore.setDomainUserPassword(username, pw) map { _ =>
            createResult
          }
      }
    }  match {
      case Success(username) =>
        replyTo ! CreateUserSuccess(username)
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onUpdateUser(message: UpdateUserRequest): Unit = {
    val UpdateUserRequest(username, firstName, lastName, displayName, email, disabled, replyTo) = message
    val domainUser = UpdateDomainUser(DomainUserId.normal(username), firstName, lastName, displayName, email, disabled)
    userStore.updateDomainUser(domainUser) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onSetPassword(message: SetPasswordRequest): Unit = {
    val SetPasswordRequest(username, password, replyTo) = message
    userStore.setDomainUserPassword(username, password) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }

  private[this] def onDeleteUser(message: DeleteUserRequest): Unit = {
    val DeleteUserRequest(username, replyTo) = message
    userStore.deleteNormalDomainUser(username) match {
      case Success(_) =>
        replyTo ! RequestSuccess()
      case Failure(cause) =>
        replyTo ! RequestFailure(cause)
    }
  }
}


object UserStoreActor {
  def apply(userStore: DomainUserStore): Behavior[Message] = Behaviors.setup { context =>
    new UserStoreActor(context, userStore)
  }

  trait Message extends CborSerializable with DomainRestMessageBody

  //
  // GetUsers
  //
  case class GetUsersRequest(filter: Option[String],
                             offset: Option[Int],
                             limit: Option[Int],
                             replyTo: ActorRef[GetUsersResponse]) extends Message

  sealed trait GetUsersResponse extends CborSerializable

  case class GetUsersSuccess(users: PagedData[DomainUser]) extends GetUsersResponse

  //
  // GetUsers
  //
  case class GetUserRequest(userId: DomainUserId, replyTo: ActorRef[GetUserResponse]) extends Message

  sealed trait GetUserResponse extends CborSerializable

  case class GetUserSuccess(user: Option[DomainUser]) extends GetUserResponse

  //
  // FindUsers
  //
  case class FindUsersRequest(filter: String,
                              exclude: Option[List[DomainUserId]],
                              offset: Option[Int],
                              limit: Option[Int],
                              replyTo: ActorRef[FindUsersResponse]) extends Message

  sealed trait FindUsersResponse extends CborSerializable

  case class FindUsersSuccess(users: PagedData[DomainUser]) extends FindUsersResponse

  //
  // CreateUser
  //

  case class CreateUserRequest(username: String,
                               firstName: Option[String],
                               lastName: Option[String],
                               displayName: Option[String],
                               email: Option[String],
                               password: Option[String],
                               replyTo: ActorRef[CreateUserResponse]) extends Message

  sealed trait CreateUserResponse extends CborSerializable

  case class CreateUserSuccess(username: String) extends CreateUserResponse

  //
  // DeleteUser
  //
  case class DeleteUserRequest(username: String, replyTo: ActorRef[DeleteUserResponse]) extends Message

  sealed trait DeleteUserResponse extends CborSerializable

  //
  // UpdateUser
  //
  case class UpdateUserRequest(username: String,
                               firstName: Option[String],
                               lastName: Option[String],
                               displayName: Option[String],
                               email: Option[String],
                               disabled: Option[Boolean],
                               replyTo: ActorRef[UpdateUserResponse]) extends Message

  sealed trait UpdateUserResponse extends CborSerializable

  //
  // SetPassword
  //
  case class SetPasswordRequest(uid: String,
                                password: String,
                                replyTo: ActorRef[SetPasswordResponse]) extends Message

  sealed trait SetPasswordResponse extends CborSerializable

  //
  // Helpers
  //
  case class RequestFailure(cause: Throwable) extends CborSerializable
    with GetUsersResponse
    with GetUserResponse
    with FindUsersResponse
    with CreateUserResponse
    with DeleteUserResponse
    with UpdateUserResponse
    with SetPasswordResponse

  case class RequestSuccess() extends CborSerializable
    with DeleteUserResponse
    with UpdateUserResponse
    with SetPasswordResponse

}
