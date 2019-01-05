package com.convergencelabs.server.domain

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.UnknownErrorResponse
import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainUserField

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.datastore.domain.UserGroup
import akka.actor.Status

object IdentityServiceActor {

  val RelativePath = "userService"

  def props(domainFqn: DomainFqn): Props = Props(
    new IdentityServiceActor(domainFqn))
}

class IdentityServiceActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  var persistenceProvider: DomainPersistenceProvider = _

  def receive: Receive = {
    case s: UserSearch => searchUsers(s)
    case l: UserLookUp => lookUpUsers(l)
    case message: UserGroupsRequest => getUserGroups(message)
    case message: UserGroupsForUsersRequest => getUserGroupsForUser(message)
    case message: IdentityResolutionRequest => resolveIdentities(message)
    case x: Any => unhandled(x)
  }

  private[this] def resolveIdentities(message: IdentityResolutionRequest): Unit = {
    (for {
      sessions <- persistenceProvider.sessionStore.getSessions(message.sessionIds)
      sesionMap <- Success(sessions.map(session => (session.id, session.username)).toMap)
      users <- persistenceProvider.userStore.getDomainUsersByUsername(
        (message.usernames ++ (sessions.map(_.username))).toList)
    } yield {
      IdentityResolutionResponse(sesionMap, users.toSet)
    }) match {
      case Success(message) => sender ! message
      case Failure(e) => sender ! Status.Failure(e)
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
      case Success(users) => sender ! UserList(users)
      case Failure(e) => sender ! Status.Failure(e)
    }
  }

  private[this] def lookUpUsers(criteria: UserLookUp): Unit = {
    val users = criteria.field match {
      case UserLookUpField.Username =>
        persistenceProvider.userStore.getDomainUsersByUsername(criteria.values)
      case UserLookUpField.Email =>
        persistenceProvider.userStore.getDomainUsersByEmail(criteria.values)
      case _ =>
        Failure(new IllegalArgumentException("Invalide user lookup field"))
    }

    users match {
      case Success(list) => sender ! UserList(list)
      case Failure(e) => sender ! UnknownErrorResponse(e.getMessage)
    }
  }

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
    val UserGroupsForUsersRequest(usernames) = request;
    persistenceProvider.userGroupStore.getUserGroupIdsForUsers(usernames) match {
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

case class UserLookUp(field: UserLookUpField.Value, values: List[String])

case class UserSearch(
  fields: List[UserLookUpField.Value],
  searchValue: String,
  offset: Option[Int],
  limit: Option[Int],
  order: Option[UserLookUpField.Value],
  sort: Option[SortOrder.Value])

case class UserList(users: List[DomainUser])

case class UserGroupsRequest(ids: Option[List[String]])
case class UserGroupsResponse(groups: List[UserGroup])

case class UserGroupsForUsersRequest(usernames: List[String])
case class UserGroupsForUsersResponse(groups: Map[String, Set[String]])

case class IdentityResolutionRequest(sessionIds: Set[String], usernames: Set[String])
case class IdentityResolutionResponse(sessionMap: Map[String, String], users: Set[DomainUser])
