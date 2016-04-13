package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.domain.DomainUser

import UserStoreActor.CreateUser
import UserStoreActor.GetUsers
import akka.actor.ActorLogging
import akka.actor.Props

object UserStoreActor {
  def props(userStore: DomainUserStore): Props = Props(new UserStoreActor(userStore))

  trait UserStoreRequest
  case object GetUsers extends UserStoreRequest
  case class CreateUser(user: DomainUser, password: String) extends UserStoreRequest
}

class UserStoreActor private[datastore] (private[this] val userStore: DomainUserStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case GetUsers => getAllUsers()
    case message: CreateUser => createUser(message)
    case message: Any => unhandled(message)
  }

  def getAllUsers(): Unit = {
    reply(userStore.getAllDomainUsers(None, None, None, None))
  }

  def createUser(message: CreateUser): Unit = {
    reply(userStore.createDomainUser(message.user, Some(message.password)))
  }
}
