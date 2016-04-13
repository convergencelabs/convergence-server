package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.domain.DomainUser

import UserStoreActor.CreateUser
import UserStoreActor.GetUsers
import akka.actor.ActorLogging
import akka.actor.Props
import java.util.UUID
import com.convergencelabs.server.datastore.UserStoreActor.GetUserByUid

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
}

class UserStoreActor private[datastore] (private[this] val userStore: DomainUserStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetUsers             => getAllUsers()
    case message: GetUserByUid => getUserById(message)
    case message: CreateUser  => createUser(message)
    case message: Any         => unhandled(message)
  }

  def getAllUsers(): Unit = {
    reply(userStore.getAllDomainUsers(None, None, None, None))
  }

  def createUser(message: CreateUser): Unit = {
    val CreateUser(username, firstName, lastName, email, password) = message
    // Fixme: Determine Correct Place to Create UID
    val uid = UUID.randomUUID().toString()
    val domainuser = DomainUser(uid, username, firstName, lastName, email)
    reply(userStore.createDomainUser(domainuser, message.password))
  }

  def getUserById(message: GetUserByUid): Unit = {
    reply(userStore.getDomainUserByUid(message.uid))
  }
}
