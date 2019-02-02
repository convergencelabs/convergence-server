package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.UserList
import com.convergencelabs.server.domain.UserLookUpField
import com.convergencelabs.server.domain.UserSearch
import com.convergencelabs.server.util.concurrent.AskFuture

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.UserGroupsRequest
import com.convergencelabs.server.domain.UserGroupsResponse
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.domain.UserGroup
import com.convergencelabs.server.domain.UserGroupsForUsersRequest
import com.convergencelabs.server.domain.UserGroupsForUsersResponse
import io.convergence.proto.Identity
import io.convergence.proto.Normal
import io.convergence.proto.identity.UserSearchMessage
import io.convergence.proto.identity.GetUsersMessage
import io.convergence.proto.identity.UserGroupData
import io.convergence.proto.identity.UserGroupsResponseMessage
import io.convergence.proto.identity.UserGroupsForUsersRequestMessage
import io.convergence.proto.identity.UserListMessage
import io.convergence.proto.identity.UserGroupsRequestMessage
import io.convergence.proto.identity.UserGroupsForUsersResponseMessage
import io.convergence.proto.identity.DomainUserData
import io.convergence.proto.identity.UserGroupsEntry
import io.convergence.proto.Request
import io.convergence.proto.common.StringList
import io.convergence.proto.identity.UserField
import com.convergencelabs.server.domain.GetUserByUsername
import com.convergencelabs.server.domain.GetUsersByUsername

object IdentityClientActor {
  def props(userServiceActor: ActorRef): Props =
    Props(new IdentityClientActor(userServiceActor))
}

class IdentityClientActor(userServiceActor: ActorRef) extends Actor with ActorLogging {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case RequestReceived(message, replyPromise) if message.isInstanceOf[Normal with Identity] =>
      onRequestReceived(message.asInstanceOf[Request with Identity], replyPromise)
    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onRequestReceived(message: Request with Identity, replyCallback: ReplyCallback): Unit = {
    message match {
      case userSearch: UserSearchMessage => onUserSearch(userSearch, replyCallback)
      case getUsersMessage: GetUsersMessage => getUsers(getUsersMessage, replyCallback)
      case userGroups: UserGroupsRequestMessage => onUserGroupsRequest(userGroups, replyCallback)
      case userGroupsForUser: UserGroupsForUsersRequestMessage => onUserGroupsForUsersRequest(userGroupsForUser, replyCallback)
    }
  }

  private[this] def onUserSearch(request: UserSearchMessage, cb: ReplyCallback): Unit = {
    val UserSearchMessage(fieldCodes, value, offset, limit, orderField, ascending) = request

    val fields = fieldCodes.map { x => mapUserField(x) }
    val orderBy = mapUserField(orderField)
    val sort = ascending match {
      case true => SortOrder.Ascending
      case _ => SortOrder.Descending
    }

    val future = this.userServiceActor ?
      UserSearch(fields.toList, value, offset, limit, Some(orderBy), Some(sort))

    future.mapResponse[UserList] onComplete {
      case Success(UserList(users)) =>
        val userData = users.map(ImplicitMessageConversions.mapDomainUser(_))
        cb.reply(UserListMessage(userData))
      case Failure(cause) =>
        val message = "Unexpected error searching users."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  private[this] def getUsers(request: GetUsersMessage, cb: ReplyCallback): Unit = {
    val GetUsersMessage(userIdData) = request
    val userIds = userIdData.map { userIdData => ImplicitMessageConversions.dataToDomainUserId(userIdData) }
    val future = this.userServiceActor ? GetUsersByUsername(userIds.toList)
    future.mapResponse[UserList] onComplete {
      case Success(UserList(users)) =>
        val userData = users.map(ImplicitMessageConversions.mapDomainUser(_))
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
    val UserGroupsRequestMessage(ids) = request;
    val message = UserGroupsRequest(Some(ids.toList))
    this.userServiceActor.ask(message).mapTo[UserGroupsResponse] onComplete {
      case Success(UserGroupsResponse(groups)) =>
        val groupData = groups.map { case UserGroup(id, desc, memebers) => UserGroupData(id, desc, memebers.map(ImplicitMessageConversions.domainUserIdToData(_)).toSeq) }
        cb.reply(UserGroupsResponseMessage(groupData))
      case Failure(EntityNotFoundException(_, Some(groupId))) =>
        cb.expectedError(
            "group_not_found", 
            s"Could not get groups because at least one group did not exist: ${groupId}", 
            Map("id" -> groupId.toString))
      case Failure(cause) =>
        val message = "Unexpected error getting groups."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  private[this] def onUserGroupsForUsersRequest(request: UserGroupsForUsersRequestMessage, cb: ReplyCallback): Unit = {
    val UserGroupsForUsersRequestMessage(users) = request;
    val message = UserGroupsForUsersRequest(users.map(ImplicitMessageConversions.dataToDomainUserId(_)).toList)
    this.userServiceActor.ask(message).mapTo[UserGroupsForUsersResponse] onComplete {
      case Success(UserGroupsForUsersResponse(groups)) =>
        val entries = groups.map{case (user, groups) => 
          (user, UserGroupsEntry(Some(ImplicitMessageConversions.domainUserIdToData(user)), groups.toSeq))
        }
        cb.reply(UserGroupsForUsersResponseMessage(entries.values.toSeq))
      case Failure(EntityNotFoundException(_, Some(userId))) =>
        cb.expectedError(
            "user_not_found",
            s"Could not get groups because at least one user did not exist: ${userId}", 
            Map("id" -> userId.toString))
      case Failure(cause) =>
        val message = "Unexpected error getting groups for users."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }
}
