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

package com.convergencelabs.convergence.server.api.realtime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.identity._
import com.convergencelabs.convergence.server.datastore.domain.UserGroup
import com.convergencelabs.convergence.server.datastore.{EntityNotFoundException, SortOrder}
import com.convergencelabs.convergence.server.domain.IdentityServiceActor._
import com.convergencelabs.convergence.server.util.concurrent.AskFuture
import org.json4s.JsonAST.JString

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * A helper actor that handles identity service related requests from the client.
 *
 * @param userServiceActor The actor to use to resolve user identity requests.
 */
private[realtime] class IdentityClientActor(userServiceActor: ActorRef) extends Actor with ActorLogging {



  implicit val timeout: Timeout = Timeout(5 seconds)
  implicit val ec: ExecutionContextExecutor = context.dispatcher

  def receive: Receive = {
    case RequestReceived(message, replyPromise) if message.isInstanceOf[RequestMessage with IdentityMessage] =>
      onRequestReceived(message.asInstanceOf[RequestMessage with IdentityMessage], replyPromise)
    case x: Any =>
      unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onRequestReceived(message: RequestMessage with IdentityMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case userSearch: UserSearchMessage =>
        onUserSearch(userSearch, replyCallback)
      case getUsersMessage: GetUsersMessage =>
        onGetUsers(getUsersMessage, replyCallback)
      case userGroups: UserGroupsRequestMessage =>
        onUserGroupsRequest(userGroups, replyCallback)
      case userGroupsForUser: UserGroupsForUsersRequestMessage =>
        onUserGroupsForUsersRequest(userGroupsForUser, replyCallback)
    }
  }

  private[this] def onUserSearch(request: UserSearchMessage, cb: ReplyCallback): Unit = {
    val UserSearchMessage(fieldCodes, value, offset, limit, orderField, ascending, _) = request

    val fields = fieldCodes.map { x => mapUserField(x) }
    val orderBy = mapUserField(orderField)
    val sort = if (ascending) {
      SortOrder.Ascending
    } else {
      SortOrder.Descending
    }

    val future = this.userServiceActor ?
      UserSearch(fields.toList, value, offset, limit, Some(orderBy), Some(sort))

    future.mapResponse[GetUsersResponse] onComplete {
      case Success(GetUsersResponse(users)) =>
        val userData = users.map(ImplicitMessageConversions.mapDomainUser)
        cb.reply(UserListMessage(userData))
      case Failure(cause) =>
        val message = "Unexpected error searching users."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  private[this] def onGetUsers(request: GetUsersMessage, cb: ReplyCallback): Unit = {
    val GetUsersMessage(userIdData, _) = request
    val userIds = userIdData.map { userIdData => ImplicitMessageConversions.dataToDomainUserId(userIdData) }
    val future = this.userServiceActor ? GetUsersRequest(userIds.toList)
    future.mapResponse[GetUsersResponse] onComplete {
      case Success(GetUsersResponse(users)) =>
        val userData = users.map(ImplicitMessageConversions.mapDomainUser)
        cb.reply(UserListMessage(userData))
      case Failure(cause) =>
        val message = "Unexpected error looking up users."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  private[this] def mapUserField(fieldCode: UserField): UserLookUpField.Value = {
    fieldCode match {
      case UserField.USERNAME => UserLookUpField.Username
      case UserField.FIRST_NAME => UserLookUpField.FirstName
      case UserField.LAST_NAME => UserLookUpField.LastName
      case UserField.DISPLAY_NAME => UserLookUpField.DisplayName
      case UserField.EMAIL => UserLookUpField.Email
      case UserField.FIELD_NOT_SET | UserField.Unrecognized(_) =>
        ???
    }
  }

  private[this] def onUserGroupsRequest(request: UserGroupsRequestMessage, cb: ReplyCallback): Unit = {
    val UserGroupsRequestMessage(ids, _) = request
    val message = UserGroupsRequest(Some(ids.toList))
    this.userServiceActor.ask(message).mapTo[GetUserGroupsResponse] onComplete {
      case Success(GetUserGroupsResponse(groups)) =>
        val groupData = groups.map { case UserGroup(id, desc, members) => UserGroupData(id, desc, members.map(ImplicitMessageConversions.domainUserIdToData).toSeq) }
        cb.reply(UserGroupsResponseMessage(groupData))
      case Failure(EntityNotFoundException(_, Some(groupId))) =>
        cb.expectedError(
          "group_not_found",
          s"Could not get groups because at least one group did not exist: $groupId",
          Map("id" -> JString(groupId.toString)))
      case Failure(cause) =>
        val message = "Unexpected error getting groups."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  private[this] def onUserGroupsForUsersRequest(request: UserGroupsForUsersRequestMessage, cb: ReplyCallback): Unit = {
    val UserGroupsForUsersRequestMessage(users, _) = request
    val message = UserGroupsForUsersRequest(users.map(ImplicitMessageConversions.dataToDomainUserId).toList)
    this.userServiceActor.ask(message).mapTo[GetUserGroupsForUsersResponse] onComplete {
      case Success(GetUserGroupsForUsersResponse(groups)) =>
        val entries = groups.map { case (user, groups) =>
          (user, UserGroupsEntry(Some(ImplicitMessageConversions.domainUserIdToData(user)), groups.toSeq))
        }
        cb.reply(UserGroupsForUsersResponseMessage(entries.values.toSeq))
      case Failure(EntityNotFoundException(_, Some(userId))) =>
        cb.expectedError(
          "user_not_found",
          s"Could not get groups because at least one user did not exist: $userId",
          Map("id" -> JString(userId.toString)))
      case Failure(cause) =>
        val message = "Unexpected error getting groups for users."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }
}

private[realtime] object IdentityClientActor {
  def props(userServiceActor: ActorRef): Props =
    Props(new IdentityClientActor(userServiceActor))
}
