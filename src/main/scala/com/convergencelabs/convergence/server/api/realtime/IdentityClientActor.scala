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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.identity._
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection.ReplyCallback
import com.convergencelabs.convergence.server.api.realtime.protocol.IdentityProtoConverters._
import com.convergencelabs.convergence.server.backend.datastore.SortOrder
import com.convergencelabs.convergence.server.backend.services.domain.identity.IdentityServiceActor
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.group.UserGroup
import com.convergencelabs.convergence.server.util.actor.AskUtils
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import grizzled.slf4j.Logging
import org.json4s.JsonAST.JString
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
 * A helper actor that handles identity service related requests from the client.
 *
 * @param identityServiceActor The actor to use to resolve user identity requests.
 */
private final class IdentityClientActor(context: ActorContext[IdentityClientActor.Message],
                                        domainId: DomainId,
                                        identityServiceActor: ActorRef[IdentityServiceActor.Message],
                                        private[this] implicit val requestTimeout: Timeout)
  extends AbstractBehavior[IdentityClientActor.Message](context) with Logging with AskUtils {

  import IdentityClientActor._

  private[this] implicit val ec: ExecutionContextExecutor = context.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case IncomingProtocolRequest(message, replyCallback) =>
        message match {
          case userSearch: SearchUsersRequestMessage =>
            onUserSearch(userSearch, replyCallback)
          case getUsersMessage: GetUsersRequestMessage =>
            onGetUsers(getUsersMessage, replyCallback)
          case userGroups: UserGroupsRequestMessage =>
            onUserGroupsRequest(userGroups, replyCallback)
          case userGroupsForUser: UserGroupsForUsersRequestMessage =>
            onUserGroupsForUsersRequest(userGroupsForUser, replyCallback)
        }
    }
    Behaviors.same
  }

  //
  // Incoming Messages
  //
  private[this] def onUserSearch(request: SearchUsersRequestMessage, cb: ReplyCallback): Unit = {
    val SearchUsersRequestMessage(fieldCodes, value, offset, limit, orderField, ascending, _) = request
    for {
      fields <- Try(fieldCodes.map(x => mapUserField(x).get)).recoverWith { cause =>
        cb.unexpectedError("Invalid search field value")
        Failure(cause)
      }
      orderBy <- mapUserField(orderField).recoverWith { cause =>
        cb.unexpectedError("Invalid order field value")
        Failure(cause)
      }
    } yield {
      val sort = if (ascending) {
        SortOrder.Ascending
      } else {
        SortOrder.Descending
      }

      identityServiceActor.ask[IdentityServiceActor.SearchUsersResponse](
        IdentityServiceActor.SearchUsersRequest(
          domainId,
          fields.toList,
          value,
          QueryOffset(offset.map(_.longValue)),
          QueryLimit(limit.map(_.longValue)),
          Some(orderBy),
          Some(sort), _))
        .map(_.users.fold({ _ =>
          cb.unexpectedError("Unexpected error searching users.")
        }, { users =>
          val userData = users.data.map(domainUserToProto)
          // FIXME update the protocol to use a paged data structure
          cb.reply(UserListMessage(userData))
        }))
        .recoverWith(handleAskFailure(_, cb))
    }
  }

  private[this] def onGetUsers(request: GetUsersRequestMessage, cb: ReplyCallback): Unit = {
    val GetUsersRequestMessage(userIdData, _) = request
    val userIds = userIdData.map { userIdData => protoToDomainUserId(userIdData) }
    identityServiceActor.ask[IdentityServiceActor.GetUsersResponse](IdentityServiceActor.GetUsersRequest(
      domainId, userIds.toList, _))
      .map(_.users.fold({
        case IdentityServiceActor.UnknownError() =>
          cb.unexpectedError("Unexpected error getting users.")
      }, { users =>
        val userData = users.map(domainUserToProto)
        cb.reply(UserListMessage(userData))
      }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onUserGroupsRequest(request: UserGroupsRequestMessage, cb: ReplyCallback): Unit = {
    val UserGroupsRequestMessage(ids, _) = request
    identityServiceActor.ask[IdentityServiceActor.GetUserGroupsResponse](
      IdentityServiceActor.GetUserGroupsRequest(domainId, Some(ids.toList), _))
      .map(_.groups.fold(
        {
          case IdentityServiceActor.GroupNotFound(notFoundId) =>
            cb.expectedError(
              ErrorCodes.GroupNotFound,
              s"Could not get groups because at least one requested group did not exist: $notFoundId",
              Map("id" -> JString(notFoundId)))

          case IdentityServiceActor.UnknownError() =>
            cb.unknownError()
        },
        { groups =>
          val groupData = groups.map { case UserGroup(id, desc, members) => UserGroupData(id, desc, members.map(domainUserIdToProto).toSeq) }
          cb.reply(UserGroupsResponseMessage(groupData))
        }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onUserGroupsForUsersRequest(request: UserGroupsForUsersRequestMessage, cb: ReplyCallback): Unit = {
    val UserGroupsForUsersRequestMessage(users, _) = request
    identityServiceActor.ask[IdentityServiceActor.GetUserGroupsForUsersResponse](
      IdentityServiceActor.GetUserGroupsForUsersRequest(domainId, users.map(protoToDomainUserId).toList, _))
      .map(_.groups.fold({
        case IdentityServiceActor.UserNotFound(userId) =>
          cb.expectedError(
            ErrorCodes.UserNotFound,
            s"Could not get groups because at least one user did not exist: $userId",
            Map("id" -> JString(userId.toString)))
        case _ =>
          cb.unexpectedError("Unexpected error getting groups for users.")
      }, { groups =>
        val entries = groups.map { case (user, groups) =>
          (user, UserGroupsEntry(Some(domainUserIdToProto(user)), groups.toSeq))
        }
        cb.reply(UserGroupsForUsersResponseMessage(entries.values.toSeq))
      }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def mapUserField(fieldCode: UserField): Try[IdentityServiceActor.UserLookUpField.Value] = {
    fieldCode match {
      case UserField.USERNAME =>
        Success(IdentityServiceActor.UserLookUpField.Username)
      case UserField.FIRST_NAME =>
        Success(IdentityServiceActor.UserLookUpField.FirstName)
      case UserField.LAST_NAME =>
        Success(IdentityServiceActor.UserLookUpField.LastName)
      case UserField.DISPLAY_NAME =>
        Success(IdentityServiceActor.UserLookUpField.DisplayName)
      case UserField.EMAIL =>
        Success(IdentityServiceActor.UserLookUpField.Email)
      case UserField.FIELD_NOT_SET | UserField.Unrecognized(_) =>
        Failure(new IllegalArgumentException("Invalid user look up field"))
    }
  }
}

object IdentityClientActor {
  private[realtime] def apply(domainId: DomainId,
                              identityServiceActor: ActorRef[IdentityServiceActor.Message],
                              requestTimeout: Timeout): Behavior[Message] =
    Behaviors.setup(context => new IdentityClientActor(context, domainId, identityServiceActor, requestTimeout))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  private[realtime] sealed trait IncomingMessage extends Message

  private[realtime] type IncomingRequest = GeneratedMessage with RequestMessage with IdentityMessage with ClientMessage

  private[realtime] final case class IncomingProtocolRequest(message: IncomingRequest, replyCallback: ReplyCallback) extends IncomingMessage

}
