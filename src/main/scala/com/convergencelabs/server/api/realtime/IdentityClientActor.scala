package com.convergencelabs.server.api.realtime

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.identity._
import com.convergencelabs.server.datastore.domain.UserGroup
import com.convergencelabs.server.datastore.{EntityNotFoundException, SortOrder}
import com.convergencelabs.server.domain._
import com.convergencelabs.server.util.concurrent.AskFuture

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
    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onRequestReceived(message: RequestMessage with IdentityMessage, replyCallback: ReplyCallback): Unit = {
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
    val sort = if (ascending) {
      SortOrder.Ascending
    } else {
      SortOrder.Descending
    }

    val future = this.userServiceActor ?
      UserSearch(fields.toList, value, offset, limit, Some(orderBy), Some(sort))

    future.mapResponse[List[DomainUser]] onComplete {
      case Success(users) =>
        val userData = users.map(ImplicitMessageConversions.mapDomainUser)
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
    future.mapResponse[List[DomainUser]] onComplete {
      case Success(users) =>
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
    val UserGroupsRequestMessage(ids) = request
    val message = UserGroupsRequest(Some(ids.toList))
    this.userServiceActor.ask(message).mapTo[UserGroupsResponse] onComplete {
      case Success(UserGroupsResponse(groups)) =>
        val groupData = groups.map { case UserGroup(id, desc, memebers) => UserGroupData(id, desc, memebers.map(ImplicitMessageConversions.domainUserIdToData).toSeq) }
        cb.reply(UserGroupsResponseMessage(groupData))
      case Failure(EntityNotFoundException(_, Some(groupId))) =>
        cb.expectedError(
          "group_not_found",
          s"Could not get groups because at least one group did not exist: $groupId",
          Map("id" -> groupId.toString))
      case Failure(cause) =>
        val message = "Unexpected error getting groups."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  private[this] def onUserGroupsForUsersRequest(request: UserGroupsForUsersRequestMessage, cb: ReplyCallback): Unit = {
    val UserGroupsForUsersRequestMessage(users) = request;
    val message = UserGroupsForUsersRequest(users.map(ImplicitMessageConversions.dataToDomainUserId).toList)
    this.userServiceActor.ask(message).mapTo[UserGroupsForUsersResponse] onComplete {
      case Success(UserGroupsForUsersResponse(groups)) =>
        val entries = groups.map { case (user, groups) =>
          (user, UserGroupsEntry(Some(ImplicitMessageConversions.domainUserIdToData(user)), groups.toSeq))
        }
        cb.reply(UserGroupsForUsersResponseMessage(entries.values.toSeq))
      case Failure(EntityNotFoundException(_, Some(userId))) =>
        cb.expectedError(
          "user_not_found",
          s"Could not get groups because at least one user did not exist: $userId",
          Map("id" -> userId.toString))
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
