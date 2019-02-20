package com.convergencelabs.server.datastore.domain

import scala.util.Success

import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateNormalDomainUser
import com.convergencelabs.server.datastore.domain.DomainUserStore.UpdateDomainUser

import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.domain.DomainUserId

object UserStoreActor {
  def props(userStore: DomainUserStore): Props = Props(new UserStoreActor(userStore))

  trait UserStoreRequest
  case class GetUsers(
    filter: Option[String],
    offset: Option[Int],
    limit: Option[Int]) extends UserStoreRequest
  case class GetUserByUsername(userId: DomainUserId) extends UserStoreRequest
  case class CreateUser(
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    displayName: Option[String],
    email: Option[String],
    password: Option[String]) extends UserStoreRequest
  case class DeleteDomainUser(username: String) extends UserStoreRequest
  case class UpdateUser(
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    displayName: Option[String],
    email: Option[String]) extends UserStoreRequest
  case class SetPassword(
    uid: String,
    password: String) extends UserStoreRequest

  case class FindUser(filter: String, exclude: Option[List[DomainUserId]], offset: Option[Int], limit: Option[Int]) extends UserStoreRequest
}

class UserStoreActor private[datastore] (private[this] val userStore: DomainUserStore)
    extends StoreActor with ActorLogging {

  import UserStoreActor._

  def receive: Receive = {
    case message: UserStoreRequest => onUserStoreRequest(message)
    case message: Any => unhandled(message)
  }

  def onUserStoreRequest(message: UserStoreRequest): Unit = {
    message match {
      case message: GetUserByUsername => getUserByUsername(message)
      case message: CreateUser => createUser(message)
      case message: DeleteDomainUser => deleteUser(message)
      case message: UpdateUser => updateUser(message)
      case message: SetPassword => setPassword(message)
      case message: GetUsers => getAllUsers(message)
      case message: FindUser => findUser(message)
    }
  }

  def getAllUsers(message: GetUsers): Unit = {
    val GetUsers(filter, offset, limit) = message
    filter match {
      case Some(filterString) =>
        reply(userStore.searchUsersByFields(
          List(DomainUserField.Username, DomainUserField.Email),
          filterString,
          Some(DomainUserField.Username),
          Some(SortOrder.Ascending),
          offset,
          limit))
      case None =>
        reply(userStore.getAllDomainUsers(Some(DomainUserField.Username), Some(SortOrder.Ascending), limit, offset))
    }
  }
  
  def findUser(message: FindUser): Unit = {
    val FindUser(search, exclude, limit, offset) = message
    reply(userStore.findUser(search, exclude.getOrElse(List()), offset.getOrElse(0), limit.getOrElse(10)))
  }

  def createUser(message: CreateUser): Unit = {
    val CreateUser(username, firstName, lastName, displayName, email, password) = message
    val domainuser = CreateNormalDomainUser(username, firstName, lastName, displayName, email)
    val result = userStore.createNormalDomainUser(domainuser) flatMap { createResult =>
      // FIXME this only works as a hack because of the way our create result works.
      password match {
        case None =>
          Success(createResult)
        case Some(pw) =>
          userStore.setDomainUserPassword(username, pw) map { _ =>
            createResult
          }
      }
    }
    reply(result)
  }

  def updateUser(message: UpdateUser): Unit = {
    val UpdateUser(username, firstName, lastName, displayName, email) = message
    val domainuser = UpdateDomainUser(username, firstName, lastName, displayName, email);
    reply(userStore.updateDomainUser(domainuser))
  }

  def setPassword(message: SetPassword): Unit = {
    val SetPassword(username, password) = message
    reply(userStore.setDomainUserPassword(username, password))
  }

  def deleteUser(message: DeleteDomainUser): Unit = {
    reply(userStore.deleteNormalDomainUser(message.username))
  }

  def getUserByUsername(message: GetUserByUsername): Unit = {
    reply(userStore.getDomainUser(message.userId))
  }
}
