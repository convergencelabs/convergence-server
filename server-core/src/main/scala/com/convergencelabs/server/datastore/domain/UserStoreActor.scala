package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.domain.DomainUser

import UserStoreActor.CreateUser
import UserStoreActor.GetUsers
import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.datastore.UserStoreActor.GetUserByUid
import com.convergencelabs.server.datastore.UserStoreActor.DeleteDomainUser
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateDomainUser
import com.convergencelabs.server.datastore.UserStoreActor.UpdateUser
import com.convergencelabs.server.datastore.UserStoreActor.SetPassword

object UserStoreActor {
  def props(userStore: DomainUserStore): Props = Props(new UserStoreActor(userStore))

  trait UserStoreRequest
  case object GetUsers extends UserStoreRequest
  case class GetUserByUid(uid: String) extends UserStoreRequest
  case class CreateUser(
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    email: Option[String],
    password: Option[String]) extends UserStoreRequest
  case class DeleteDomainUser(uid: String) extends UserStoreRequest
  case class UpdateUser(
    uid: String,
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    email: Option[String]) extends UserStoreRequest
  case class SetPassword(
    uid: String,
    password: String) extends UserStoreRequest
}

class UserStoreActor private[datastore] (private[this] val userStore: DomainUserStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetUsers                  => getAllUsers()
    case message: GetUserByUid     => getUserById(message)
    case message: CreateUser       => createUser(message)
    case message: DeleteDomainUser => deleteUser(message)
    case message: UpdateUser       => updateUser(message)
    case message: SetPassword      => setPassword(message)
    case message: Any              => unhandled(message)
  }

  def getAllUsers(): Unit = {
    reply(userStore.getAllDomainUsers(None, None, None, None))
  }

  def createUser(message: CreateUser): Unit = {
    val CreateUser(username, firstName, lastName, email, password) = message
    val domainuser = CreateDomainUser(username, firstName, lastName, email)
    reply(userStore.createDomainUser(domainuser, password))
  }

  def updateUser(message: UpdateUser): Unit = {
    val UpdateUser(uid, username, firstName, lastName, email) = message
    val domainuser = DomainUser(uid, username, firstName, lastName, email);
    reply(userStore.updateDomainUser(domainuser))
  }

  def setPassword(message: SetPassword): Unit = {
    val SetPassword(uid, password) = message
    reply(userStore.setDomainUserPassword(uid, password))
  }

  def getUserById(message: GetUserByUid): Unit = {
    reply(userStore.getDomainUserByUid(message.uid))
  }

  def deleteUser(message: DeleteDomainUser): Unit = {
    reply(userStore.deleteDomainUser(message.uid))
  }
}
