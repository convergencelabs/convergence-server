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

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.convergence.server.UnknownErrorResponse
import com.convergencelabs.convergence.server.datastore.SortOrder
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.convergence.server.datastore.domain.DomainUserField

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.convergence.server.datastore.domain.UserGroup
import akka.actor.Status
import com.convergencelabs.convergence.server.datastore.EntityNotFoundException

object IdentityServiceActor {

  val RelativePath = "userService"

  def props(domainFqn: DomainId): Props = Props(
    new IdentityServiceActor(domainFqn))
}

class IdentityServiceActor private[domain] (domainFqn: DomainId) extends Actor with ActorLogging {

  var persistenceProvider: DomainPersistenceProvider = _

  def receive: Receive = {
    case s: UserSearch => searchUsers(s)
    //    case l: UserLookUp => lookUpUsers(l)
    case GetUsersByUsername(userIds) =>
      getUsersByUsername(userIds)
    case GetUserByUsername(userId) =>
      getUserByUsername(userId)
    case message: UserGroupsRequest =>
      getUserGroups(message)
    case message: UserGroupsForUsersRequest =>
      getUserGroupsForUser(message)
    case message: IdentityResolutionRequest =>
      resolveIdentities(message)
    case x: Any => unhandled(x)
  }

  private[this] def getUsersByUsername(userIds: List[DomainUserId]): Unit = {
    persistenceProvider.userStore.getDomainUsers(userIds) match {
      case Success(users) => sender ! users
      case Failure(e) => sender ! Status.Failure(e)
    }
  }

  private[this] def getUserByUsername(userId: DomainUserId): Unit = {
    persistenceProvider.userStore.getDomainUser(userId).map {
      _ match {
        case Some(user) => sender ! user
        case None => sender ! Status.Failure(EntityNotFoundException())
      }
    } recover {
      case cause => sender ! Status.Failure(cause)
    }
  }

  private[this] def resolveIdentities(message: IdentityResolutionRequest): Unit = {
    log.debug("Processing identity resolution: {}", message)
    try {
      (for {
        sessions <- persistenceProvider.sessionStore.getSessions(message.sessionIds)
        sesionMap <- Success(sessions.map(session => (session.id, session.userId)).toMap)
        users <- persistenceProvider.userStore.getDomainUsers(
          (message.userIds ++ (sessions.map(_.userId))).toList)
      } yield {
        log.debug("resolved")
        IdentityResolutionResponse(sesionMap, users.toSet)
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
    val fields = criteria.fields.map { f => converField(f) }
    val order = criteria.order.map { x => converField(x) }
    val limit = criteria.limit
    val offset = criteria.offset
    val sortOrder = criteria.sort

    persistenceProvider.userStore.searchUsersByFields(fields, searchString, order, sortOrder, limit, offset) match {
      case Success(users) => sender ! users
      case Failure(e) => sender ! Status.Failure(e)
    }
  }

  //  private[this] def lookUpUsers(criteria: UserLookUp): Unit = {
  //    val users = criteria.field match {
  //      case UserLookUpField.Username =>
  //        persistenceProvider.userStore.getDomainUsersByUsername(criteria.values)
  //      case UserLookUpField.Email =>
  //        persistenceProvider.userStore.getDomainUsersByEmail(criteria.values)
  //      case _ =>
  //        Failure(new IllegalArgumentException("Invalide user lookup field"))
  //    }
  //
  //    users match {
  //      case Success(list) => sender ! UserList(list)
  //      case Failure(e) => sender ! UnknownErrorResponse(e.getMessage)
  //    }
  //  }

  private[this] def getUserGroups(request: UserGroupsRequest): Unit = {
    val UserGroupsRequest(ids) = request;
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

  private[this] def getUserGroupsForUser(request: UserGroupsForUsersRequest): Unit = {
    val UserGroupsForUsersRequest(userIds) = request;
    persistenceProvider.userGroupStore.getUserGroupIdsForUsers(userIds) match {
      case Success(result) =>
        sender ! UserGroupsForUsersResponse(result)
      case Failure(cause) =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def converField(field: UserLookUpField.Value): DomainUserField.Field = {
    field match {
      case UserLookUpField.Username => DomainUserField.Username
      case UserLookUpField.FirstName => DomainUserField.FirstName
      case UserLookUpField.LastName => DomainUserField.LastName
      case UserLookUpField.DisplayName => DomainUserField.DisplayName
      case UserLookUpField.Email => DomainUserField.Email
    }
  }

  override def postStop(): Unit = {
    log.debug(s"UserServiceActor(${domainFqn}) stopped.")
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }

  override def preStart(): Unit = {
    // FIXME Handle none better with logging.
    persistenceProvider = DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn).get
  }
}

object UserLookUpField extends Enumeration {
  val Username, FirstName, LastName, DisplayName, Email = Value
}

case class GetUsersByUsername(userIds: List[DomainUserId])
case class GetUserByUsername(userId: DomainUserId)

//case class UserLookUp(field: UserLookUpField.Value, values: List[String])

case class UserSearch(
  fields: List[UserLookUpField.Value],
  searchValue: String,
  offset: Option[Int],
  limit: Option[Int],
  order: Option[UserLookUpField.Value],
  sort: Option[SortOrder.Value])

case class UserGroupsRequest(ids: Option[List[String]])
case class UserGroupsResponse(groups: List[UserGroup])

case class UserGroupsForUsersRequest(userIds: List[DomainUserId])
case class UserGroupsForUsersResponse(groups: Map[DomainUserId, Set[String]])

case class IdentityResolutionRequest(sessionIds: Set[String], userIds: Set[DomainUserId])
case class IdentityResolutionResponse(sessionMap: Map[String, DomainUserId], users: Set[DomainUser])
