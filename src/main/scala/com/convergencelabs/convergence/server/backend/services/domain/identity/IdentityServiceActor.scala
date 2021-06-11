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

package com.convergencelabs.convergence.server.backend.services.domain.identity

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserField
import com.convergencelabs.convergence.server.backend.datastore.{EntityNotFoundException, SortOrder}
import com.convergencelabs.convergence.server.backend.services.domain.{DomainPersistenceManager, BaseDomainShardedActor}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.group.UserGroup
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.concurrent.duration.FiniteDuration
import scala.util.Success

/**
 * The IdentityServiceActor provides information on users and groups in the system.
 */
class IdentityServiceActor(domainId: DomainId,
                                   context: ActorContext[IdentityServiceActor.Message],
                                   shardRegion: ActorRef[IdentityServiceActor.Message],
                                   shard: ActorRef[ClusterSharding.ShardCommand],
                                   domainPersistenceManager: DomainPersistenceManager,
                                   receiveTimeout: FiniteDuration)
  extends BaseDomainShardedActor[IdentityServiceActor.Message](domainId, context, shardRegion, shard, domainPersistenceManager, receiveTimeout) {

  import IdentityServiceActor._

  override def receiveInitialized(msg: Message): Behavior[Message] = {
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
      case ReceiveTimeout(_) =>
        this.passivate()
    }
  }

  private[this] def onGetUsersRequest(msg: GetUsersRequest): Behavior[Message] = {
    val GetUsersRequest(_, userIds, replyTo) = msg
    persistenceProvider.userStore.getDomainUsers(userIds)
      .map(users => Right(users))
      .recover { cause =>
        logRequestError(msg, cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! GetUsersResponse(_))

    Behaviors.same
  }

  private[this] def onGetUserRequest(msg: GetUserRequest): Behavior[Message] = {
    val GetUserRequest(_, userId, replyTo) = msg
    persistenceProvider.userStore.getDomainUser(userId)
      .map {
        case Some(user) =>
          Right(user)
        case None =>
          Left(UserNotFound(userId))
      }
      .recover { cause =>
        logRequestError(msg, cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! GetUserResponse(_))

    Behaviors.same
  }

  private[this] def resolveIdentities(msg: IdentityResolutionRequest): Behavior[Message] = {
    debug(s"Processing identity resolution: $msg")
    val IdentityResolutionRequest(_, sessionIds, userIds, replyTo) = msg
    (for {
      sessions <- persistenceProvider.sessionStore.getSessions(sessionIds)
      sessionMap <- Success(sessions.map(session => (session.id, session.userId)).toMap)
      users <- persistenceProvider.userStore.getDomainUsers(
        (userIds ++ sessions.map(_.userId)).toList)
    } yield IdentityResolution(sessionMap, users.toSet))
      .map(r => Right(r))
      .recover { cause =>
        logRequestError(msg, cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! IdentityResolutionResponse(_))

    Behaviors.same
  }

  private[this] def onSearchUsersRequest(msg: SearchUsersRequest): Behavior[Message] = {
    val SearchUsersRequest(_, fields, searchValue, offset, limit, order, sort, replyTo) = msg
    val f = fields.map(convertField)
    val o = order.map(convertField)
    persistenceProvider.userStore.searchUsersByFields(f, searchValue, o, sort, offset, limit)
      .map(users => Right(users))
      .recover { cause =>
        logRequestError(msg, cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! SearchUsersResponse(_))

    Behaviors.same
  }

  private[this] def onGetUserGroups(msg: GetUserGroupsRequest): Behavior[Message] = {
    val GetUserGroupsRequest(_, ids, replyTo) = msg
    (ids match {
      case Some(idList) =>
        persistenceProvider.userGroupStore.getUserGroupsById(idList)
      case None =>
        persistenceProvider.userGroupStore.getUserGroups(None, QueryOffset(), QueryLimit())
    })
      .map(groups => Right(groups))
      .recover {
        case EntityNotFoundException(_, Some(entityId)) =>
          Left(GroupNotFound(entityId.toString))
        case cause =>
          logRequestError(msg, cause)
          Left(UnknownError())
      }
      .foreach(replyTo ! GetUserGroupsResponse(_))

    Behaviors.same
  }

  private[this] def onGetUserGroupsForUser(msg: GetUserGroupsForUsersRequest): Behavior[Message] = {
    val GetUserGroupsForUsersRequest(_, userIds, replyTo) = msg
    persistenceProvider.userGroupStore.getUserGroupIdsForUsers(userIds)
      .map(groups => Right(groups))
      .recover { cause =>
        logRequestError(msg, cause)
        Left(UnknownError())
      }
      .foreach(replyTo ! GetUserGroupsForUsersResponse(_))

    Behaviors.same
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

  override protected def getDomainId(msg: Message): DomainId = msg.domainId

  override protected def getReceiveTimeoutMessage(): Message = ReceiveTimeout(this.domainId)
}

object IdentityServiceActor {
  def apply(domainId: DomainId,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration): Behavior[Message] = Behaviors.setup { context =>
    new IdentityServiceActor(
      domainId,
      context,
      shardRegion,
      shard,
      domainPersistenceManager,
      receiveTimeout)
  }

  object UserLookUpField extends Enumeration {
    val Username, FirstName, LastName, DisplayName, Email = Value
  }

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////
  sealed trait Message extends CborSerializable {
    val domainId: DomainId
  }

  private final case class ReceiveTimeout(domainId: DomainId) extends Message

  //
  // SearchUsers
  //
  final case class SearchUsersRequest(domainId: DomainId,
                                      fields: List[UserLookUpField.Value],
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
  final case class GetUsersRequest(domainId: DomainId,
                                   userIds: List[DomainUserId],
                                   replyTo: ActorRef[GetUsersResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetUsersError

  final case class GetUsersResponse(users: Either[GetUsersError, List[DomainUser]]) extends CborSerializable


  //
  // GetUserRequest
  //
  final case class GetUserRequest(domainId: DomainId,
                                  userId: DomainUserId,
                                  replyTo: ActorRef[GetUserResponse]) extends Message

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
  final case class GetUserGroupsRequest(domainId: DomainId,
                                        ids: Option[List[String]],
                                        replyTo: ActorRef[GetUserGroupsResponse]) extends Message

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
  final case class IdentityResolutionRequest(domainId: DomainId,
                                             sessionIds: Set[String],
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
  final case class GetUserGroupsForUsersRequest(domainId: DomainId,
                                                userIds: List[DomainUserId],
                                                replyTo: ActorRef[GetUserGroupsForUsersResponse]) extends Message

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