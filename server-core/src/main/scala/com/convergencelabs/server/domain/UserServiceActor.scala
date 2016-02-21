package com.convergencelabs.server.domain

import akka.actor.Actor
import akka.actor.ActorLogging
import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.datastore.domain.DomainUserField
import com.convergencelabs.server.datastore.SortOrder
import scala.util.Failure
import com.convergencelabs.server.UnknownErrorResponse
import scala.util.Success
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import akka.actor.Props

object UserServiceActor {

  val RelativePath = "userService"

  def props(domainFqn: DomainFqn): Props = Props(
    new UserServiceActor(domainFqn))
}


class UserServiceActor private[domain] (domainFqn: DomainFqn) extends Actor with ActorLogging {

  var persistenceProvider: DomainPersistenceProvider = _

  def receive: Receive = {
    case s: UserSearch => searchUsers(s)
    case l: UserLookUp => lookUpUsers(l)
    case x: Any => unhandled(x)
  }

  def searchUsers(criteria: UserSearch): Unit = {

    val searchString = criteria.searchValue
    val fields = criteria.fields.map { f => converField(f) }
    val order = criteria.order.map { x => converField(x) }
    val limit = criteria.limit
    val offset = criteria.offset
    val sortOrder = criteria.sort

    persistenceProvider.userStore.searchUsersByFields(fields, searchString, order, sortOrder, limit, offset) match {
      case Success(users) => sender ! UserList(users)
      case Failure(e) => sender ! UnknownErrorResponse("Unable to get users")
    }
  }

  def lookUpUsers(criteria: UserLookUp): Unit = {
    val users = criteria.field match {
      case UserLookUpField.UserId =>
        persistenceProvider.userStore.getDomainUsersByUid(criteria.values)
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

  def converField(field: UserLookUpField.Value): DomainUserField.Field = {
    field match {
      case UserLookUpField.UserId => DomainUserField.UserId
      case UserLookUpField.Username => DomainUserField.Username
      case UserLookUpField.FirstName => DomainUserField.FirstName
      case UserLookUpField.LastName => DomainUserField.LastName
      case UserLookUpField.Email => DomainUserField.Email
    }
  }
  
  override def postStop(): Unit = {
    log.debug("ModelManagerActor({}) received shutdown command.  Shutting down all Realtime Models.", this.domainFqn)
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }

  override def preStart(): Unit = {
    // FIXME Handle none better with logging.
    persistenceProvider = DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn).get
  }
}

object UserLookUpField extends Enumeration {
  val UserId, Username, FirstName, LastName, Email = Value
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
