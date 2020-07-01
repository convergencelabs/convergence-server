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

package com.convergencelabs.convergence.server.backend.services.domain

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.backend.datastore.domain._
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserField
import com.convergencelabs.convergence.server.backend.datastore.{EntityNotFoundException, SortOrder}
import com.convergencelabs.convergence.server.model.domain.group.UserGroup
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import grizzled.slf4j.Logging

import scala.util.Success

/**
 * The IdentityServiceActor provides information on users and groups in the system.
 *
 * @param context             The ActorContext for this actor.
 * @param persistenceProvider The persistence provider to use.
 */
class IdentityServiceActor private[domain](context: ActorContext[IdentityServiceActor.Message],
                                           persistenceProvider: DomainPersistenceProvider)

  extends AbstractBehavior[IdentityServiceActor.Message](context)
    with Logging {

  import IdentityServiceActor._

  override def onMessage(msg: Message): Behavior[Message] = {
    msg match {
      case s: SearchUsersRequest =>
        onSearchUsersRequest(s)
      case msg: GetUsersRequest =>
        onGetUsersRequest(msg)
      case msg: GetUserRequest =>
        onGetUserRequest(msg)
      case message: GetUserGroupsRequest =>
        onGetUserGroups(message)
      case message: GetUserGroupsForUsersRequest =>
        onGetUserGroupsForUser(message)
      case message: IdentityResolutionRequest =>
        resolveIdentities(message)
    }

    Behaviors.same
  }

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = {
    case PostStop =>
      debug(s"IdentityServiceActor(${persistenceProvider.domainId}) stopped.")
      Behaviors.same
  }

  private[this] def onGetUsersRequest(msg: GetUsersRequest): Unit = {
    val GetUsersRequest(userIds, replyTo) = msg
    persistenceProvider.userStore.getDomainUsers(userIds)
      .map(users => GetUsersResponse(Right(users)))
      .recover { cause =>
        logRequestError(msg, cause)
        GetUsersResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetUserRequest(msg: GetUserRequest): Unit = {
    val GetUserRequest(userId, replyTo) = msg
    persistenceProvider.userStore.getDomainUser(userId)
      .map {
        case Some(user) =>
          GetUserResponse(Right(user))
        case None =>
          GetUserResponse(Left(UserNotFound(userId)))
      }
      .recover { cause =>
        logRequestError(msg, cause)
        GetUserResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def resolveIdentities(msg: IdentityResolutionRequest): Unit = {
    debug(s"Processing identity resolution: $msg")
    val IdentityResolutionRequest(sessionIds, userIds, replyTo) = msg
    (for {
      sessions <- persistenceProvider.sessionStore.getSessions(sessionIds)
      sessionMap <- Success(sessions.map(session => (session.id.sessionId, session.id.userId)).toMap)
      users <- persistenceProvider.userStore.getDomainUsers(
        (userIds ++ sessions.map(_.id.userId)).toList)
    } yield IdentityResolution(sessionMap, users.toSet))
      .map(r => IdentityResolutionResponse(Right(r)))
      .recover { cause =>
        logRequestError(msg, cause)
        IdentityResolutionResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onSearchUsersRequest(msg: SearchUsersRequest): Unit = {
    val SearchUsersRequest(fields, searchValue, offset, limit, order, sort, replyTo) = msg
    val f = fields.map(convertField)
    val o = order.map(convertField)
    persistenceProvider.userStore.searchUsersByFields(f, searchValue, o, sort, offset, limit)
      .map(users => SearchUsersResponse(Right(users)))
      .recover { cause =>
        logRequestError(msg, cause)
        SearchUsersResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetUserGroups(msg: GetUserGroupsRequest): Unit = {
    val GetUserGroupsRequest(ids, replyTo) = msg
    (ids match {
      case Some(idList) =>
        persistenceProvider.userGroupStore.getUserGroupsById(idList)
      case None =>
        persistenceProvider.userGroupStore.getUserGroups(None, QueryOffset(), QueryLimit())
    })
      .map(groups => GetUserGroupsResponse(Right(groups)))
      .recover {
        case EntityNotFoundException(_, Some(entityId)) =>
          GetUserGroupsResponse(Left(GroupNotFound(entityId.toString)))
        case cause =>
          logRequestError(msg, cause)
          GetUserGroupsResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def onGetUserGroupsForUser(msg: GetUserGroupsForUsersRequest): Unit = {
    val GetUserGroupsForUsersRequest(userIds, replyTo) = msg
    persistenceProvider.userGroupStore.getUserGroupIdsForUsers(userIds)
      .map(groups => GetUserGroupsForUsersResponse(Right(groups)))
      .recover { cause =>
        logRequestError(msg, cause)
        GetUserGroupsForUsersResponse(Left(UnknownError()))
      }
      .foreach(replyTo ! _)
  }

  private[this] def logRequestError(request: Any, cause: Throwable): Unit = {
    error(s"Unexpected error handling request: $request", cause)
  }

  private[this] def convertField(field: UserLookUpField.Value): DomainUserField.Field = {
    field match {
      case UserLookUpField.Username => DomainUserField.Username
      case UserLookUpField.FirstName => DomainUserField.FirstName
      case UserLookUpField.LastName => DomainUserField.LastName
      case UserLookUpField.DisplayName => DomainUserField.DisplayName
      case UserLookUpField.Email => DomainUserField.Email
    }
  }
}

object IdentityServiceActor {
  def apply(provider: DomainPersistenceProvider): Behavior[Message] = Behaviors.setup { context =>
    new IdentityServiceActor(context, provider)
  }

  object UserLookUpField extends Enumeration {
    val Username, FirstName, LastName, DisplayName, Email = Value
  }

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////
  sealed trait Message extends CborSerializable

  //
  // SearchUsers
  //
  final case class SearchUsersRequest(fields: List[UserLookUpField.Value],
                                      searchValue: String,
                                      @JsonDeserialize(contentAs = classOf[Long])
                                      offset: QueryOffset,
                                      @JsonDeserialize(contentAs = classOf[Long])
                                      limit: QueryLimit,
                                      order: Option[UserLookUpField.Value],
                                      sort: Option[SortOrder.Value],
                                      replyTo: ActorRef[SearchUsersResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SearchUsersError

  final case class SearchUsersResponse(users: Either[SearchUsersError, PagedData[DomainUser]]) extends CborSerializable

  //
  // GetUsersRequest
  //
  final case class GetUsersRequest(userIds: List[DomainUserId], replyTo: ActorRef[GetUsersResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUsersError

  final case class GetUsersResponse(users: Either[GetUsersError, List[DomainUser]]) extends CborSerializable


  //
  // GetUserRequest
  //
  final case class GetUserRequest(userId: DomainUserId, replyTo: ActorRef[GetUserResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFound], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserError

  final case class GetUserResponse(user: Either[GetUserError, DomainUser]) extends CborSerializable


  //
  // GetUserGroups
  //
  final case class GetUserGroupsRequest(ids: Option[List[String]], replyTo: ActorRef[GetUserGroupsResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserGroupsError

  final case class GroupNotFound(groupId: String) extends GetUserGroupsError

  final case class GetUserGroupsResponse(groups: Either[GetUserGroupsError, List[UserGroup]]) extends CborSerializable


  //
  // IdentityResolution
  //
  final case class IdentityResolutionRequest(sessionIds: Set[String],
                                             userIds: Set[DomainUserId],
                                             replyTo: ActorRef[IdentityResolutionResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait IdentityResolutionError

  final case class IdentityResolutionResponse(resolution: Either[IdentityResolutionError, IdentityResolution]) extends CborSerializable

  final case class IdentityResolution(sessionMap: Map[String, DomainUserId], users: Set[DomainUser])


  //
  // GetUserGroupsForUsers
  //
  final case class GetUserGroupsForUsersRequest(userIds: List[DomainUserId], replyTo: ActorRef[GetUserGroupsForUsersResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UserNotFound], name = "user_not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUserGroupsForUsersError

  final case class GetUserGroupsForUsersResponse(groups: Either[GetUserGroupsForUsersError, Map[DomainUserId, Set[String]]]) extends CborSerializable

  //
  // Common Errors
  //
  final case class UserNotFound(userId: DomainUserId)
    extends GetUserError
      with GetUserGroupsForUsersError

  final case class UnknownError()
    extends GetUserError
      with GetUsersError
      with GetUserGroupsForUsersError
      with SearchUsersError
      with IdentityResolutionError
      with GetUserGroupsError

}