package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.UserList
import com.convergencelabs.server.domain.UserLookUp
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

object IdentityClientActor {
  def props(userServiceActor: ActorRef): Props =
    Props(new IdentityClientActor(userServiceActor))
}

class IdentityClientActor(userServiceActor: ActorRef) extends Actor with ActorLogging {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case RequestReceived(message, replyPromise) if message.isInstanceOf[IncomingIdentityMessage] =>
      onRequestReceived(message.asInstanceOf[IncomingIdentityMessage], replyPromise)
    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onRequestReceived(message: IncomingIdentityMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case userSearch: UserSearchMessage => onUserSearch(userSearch, replyCallback)
      case userLookUp: UserLookUpMessage => onUserLookUp(userLookUp, replyCallback)
      case userGroups: UserGroupsRequestMessage => onUserGroupsRequest(userGroups, replyCallback)
      case userGroupsForUser: UserGroupsForUsersRequestMessage => onUserGroupsForUsersRequest(userGroupsForUser, replyCallback)
    }
  }

  private[this] def onUserSearch(request: UserSearchMessage, cb: ReplyCallback): Unit = {
    val UserSearchMessage(fieldCodes, value, offset, limit, orderFieldCode, sortCode) = request

    val fields = fieldCodes.map { x => mapUserField(x) }
    val orderBy = orderFieldCode.map { x => mapUserField(x) }
    val sort = sortCode.map {
      case 0 => SortOrder.Ascending
      case _ => SortOrder.Descending
    }

    val future = this.userServiceActor ?
      UserSearch(fields, value, offset, limit, orderBy, sort)

    future.mapResponse[UserList] onComplete {
      case Success(UserList(users)) =>
        val userData = users.map { x => mapDomainUser(x) }
        cb.reply(UserListMessage(userData))
      case Failure(cause) =>
        val message = "Unexpected error searching users."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  private[this] def onUserLookUp(request: UserLookUpMessage, cb: ReplyCallback): Unit = {
    val UserLookUpMessage(fieldCode, values) = request
    val field = mapUserField(fieldCode)
    val future = this.userServiceActor ? UserLookUp(field, values)
    future.mapResponse[UserList] onComplete {
      case Success(UserList(users)) =>
        val userData = users.map { x => mapDomainUser(x) }
        cb.reply(UserListMessage(userData))
      case Failure(cause) => 
        val message = "Unexpected error looking up users."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  private[this] def mapUserField(fieldCode: Int): UserLookUpField.Value = {
    fieldCode match {
      case UserFieldCodes.Username => UserLookUpField.Username
      case UserFieldCodes.FirstName => UserLookUpField.FirstName
      case UserFieldCodes.LastName => UserLookUpField.LastName
      case UserFieldCodes.DisplayName => UserLookUpField.DisplayName
      case UserFieldCodes.Email => UserLookUpField.Email
    }
  }

  private[this] def onUserGroupsRequest(request: UserGroupsRequestMessage, cb: ReplyCallback): Unit = {
    val UserGroupsRequestMessage(ids) = request;
    val message = UserGroupsRequest(ids)
    this.userServiceActor.ask(message).mapTo[UserGroupsResponse] onComplete {
      case Success(UserGroupsResponse(groups)) =>
        val groupData = groups.map { case UserGroup(id, desc, memebers) => UserGroupData(id, desc, memebers) }
        cb.reply(UserGroupsResponseMessage(groupData))
      case Failure(EntityNotFoundException(_, Some(groupId))) =>
        cb.expectedError(
            "group_not_found", 
            s"Could not get groups because at least one group did not exist: ${groupId}", 
            Map("id" -> groupId))
      case Failure(cause) =>
        val message = "Unexpected error getting groups."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  private[this] def onUserGroupsForUsersRequest(request: UserGroupsForUsersRequestMessage, cb: ReplyCallback): Unit = {
    val UserGroupsForUsersRequestMessage(usernames) = request;
    val message = UserGroupsForUsersRequest(usernames)
    this.userServiceActor.ask(message).mapTo[UserGroupsForUsersResponse] onComplete {
      case Success(UserGroupsForUsersResponse(groups)) =>
        cb.reply(UserGroupsForUsersResponseMessage(groups))
      case Failure(EntityNotFoundException(_, Some(userId))) =>
        cb.expectedError(
            "user_not_found",
            s"Could not get groups because at least one user did not exist: ${userId}", 
            Map("id" -> userId))
      case Failure(cause) =>
        val message = "Unexpected error getting groups for users."
        log.error(cause, message)
        cb.unexpectedError(message)
    }
  }

  private[this] def mapDomainUser(user: DomainUser): DomainUserData = {
    val DomainUser(userType, username, firstname, lastName, displayName, email) = user
    DomainUserData(userType.toString(), username, firstname, lastName, displayName, email)
  }

  private[this] object UserFieldCodes extends Enumeration {
    val UserId = 0;
    val Username = 1;
    val FirstName = 2;
    val LastName = 3;
    val DisplayName = 4;
    val Email = 5;
  }
}
