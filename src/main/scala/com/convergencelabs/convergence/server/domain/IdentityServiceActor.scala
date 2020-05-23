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

package com.convergencelabs.convergence.server.domain

import akka.actor.{Actor, ActorLogging, Props, Status}
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.datastore.domain.{DomainPersistenceManagerActor, DomainPersistenceProvider, DomainUserField, UserGroup}
import com.convergencelabs.convergence.server.datastore.{EntityNotFoundException, SortOrder}

import scala.util.{Failure, Success}


class IdentityServiceActor private[domain](domainFqn: DomainId) extends Actor with ActorLogging {

  var persistenceProvider: DomainPersistenceProvider = _

  import IdentityServiceActor._

  def receive: Receive = {
    case s: UserSearch =>
      searchUsers(s)
    case GetUsersByUsername(userIds) =>
      onGetUsersByUsername(userIds)
    case GetUserByUsername(userId) =>
      onGetUserByUsername(userId)
    case message: UserGroupsRequest =>
      onGetUserGroups(message)
    case message: UserGroupsForUsersRequest =>
      onGetUserGroupsForUser(message)
    case message: IdentityResolutionRequest =>
      resolveIdentities(message)
    case x: Any => unhandled(x)
  }

  private[this] def onGetUsersByUsername(userIds: List[DomainUserId]): Unit = {
    persistenceProvider.userStore.getDomainUsers(userIds) match {
      case Success(users) => sender !UsersResponse(users)
      case Failure(e) => sender ! Status.Failure(e)
    }
  }

  private[this] def onGetUserByUsername(userId: DomainUserId): Unit = {
    persistenceProvider.userStore.getDomainUser(userId).map {
      case Some(user) => sender ! UserResponse(user)
      case None => sender ! Status.Failure(EntityNotFoundException())
    } recover {
      case cause => sender ! Status.Failure(cause)
    }
  }

  private[this] def resolveIdentities(message: IdentityResolutionRequest): Unit = {
    log.debug("Processing identity resolution: {}", message)
    try {
      (for {
        sessions <- persistenceProvider.sessionStore.getSessions(message.sessionIds)
        sessionMap <- Success(sessions.map(session => (session.id, session.userId)).toMap)
        users <- persistenceProvider.userStore.getDomainUsers(
          (message.userIds ++ sessions.map(_.userId)).toList)
      } yield {
        log.debug("resolved")
        IdentityResolutionResponse(sessionMap, users.toSet)
      }) match {
        case Success(response) => sender ! response
        case Failure(e) =>
          log.error(e, "failed")
          sender ! Status.Failure(e)
      }
    } catch {
      case e: Throwable => log.error(e, "oops")
    }
  }

  private[this] def searchUsers(criteria: UserSearch): Unit = {
    val searchString = criteria.searchValue
    val fields = criteria.fields.map { f => convertField(f) }
    val order = criteria.order.map { x => convertField(x) }
    val limit = criteria.limit
    val offset = criteria.offset
    val sortOrder = criteria.sort

    persistenceProvider.userStore.searchUsersByFields(fields, searchString, order, sortOrder, limit, offset) match {
      case Success(users) => sender ! UsersResponse(users)
      case Failure(e) => sender ! Status.Failure(e)
    }
  }

  private[this] def onGetUserGroups(request: UserGroupsRequest): Unit = {
    val UserGroupsRequest(ids) = request
    (ids match {
      case Some(idList) =>
        persistenceProvider.userGroupStore.getUserGroupsById(idList)
      case None =>
        persistenceProvider.userGroupStore.getUserGroups(None, None, None)
    }) match {
      case Success(groups) =>
        sender ! UserGroupsResponse(groups)
      case Failure(cause) =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def onGetUserGroupsForUser(request: UserGroupsForUsersRequest): Unit = {
    val UserGroupsForUsersRequest(userIds) = request
    persistenceProvider.userGroupStore.getUserGroupIdsForUsers(userIds) match {
      case Success(result) =>
        sender ! UserGroupsForUsersResponse(result)
      case Failure(cause) =>
        sender ! Status.Failure(cause)
    }
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

  override def postStop(): Unit = {
    log.debug(s"UserServiceActor($domainFqn) stopped.")
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }

  override def preStart(): Unit = {
    persistenceProvider = DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn).get
  }
}


object IdentityServiceActor {

  val RelativePath = "userService"

  def props(domainFqn: DomainId): Props = Props(
    new IdentityServiceActor(domainFqn))


  object UserLookUpField extends Enumeration {
    val Username, FirstName, LastName, DisplayName, Email = Value
  }

  case class UserSearch(fields: List[UserLookUpField.Value],
                        searchValue: String,
                        offset: Option[Int],
                        limit: Option[Int],
                        order: Option[UserLookUpField.Value],
                        sort: Option[SortOrder.Value])

  trait IdentityServiceActorMessage extends CborSerializable

  case class GetUsersByUsername(userIds: List[DomainUserId]) extends IdentityServiceActorMessage

  case class GetUserByUsername(userId: DomainUserId) extends IdentityServiceActorMessage

  case class UserGroupsRequest(ids: Option[List[String]]) extends IdentityServiceActorMessage

  case class IdentityResolutionRequest(sessionIds: Set[String], userIds: Set[DomainUserId]) extends IdentityServiceActorMessage

  case class UserGroupsForUsersRequest(userIds: List[DomainUserId]) extends IdentityServiceActorMessage


  case class UsersResponse(users: List[DomainUser]) extends CborSerializable
  case class UserResponse(user: DomainUser) extends CborSerializable

  case class UserGroupsResponse(groups: List[UserGroup]) extends CborSerializable

  case class UserGroupsForUsersResponse(groups: Map[DomainUserId, Set[String]]) extends CborSerializable

  case class IdentityResolutionResponse(sessionMap: Map[String, DomainUserId], users: Set[DomainUser]) extends CborSerializable
}