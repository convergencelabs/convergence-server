package com.convergencelabs.server.datastore

import com.convergencelabs.server.datastore.UserStoreActor.CreateUserRequest
import com.convergencelabs.server.datastore.UserStoreActor.CreateUserResponse
import com.convergencelabs.server.datastore.UserStoreActor.GetAllUsersRequest
import com.convergencelabs.server.datastore.UserStoreActor.GetAllUsersResponse
import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.domain.DomainUser

import akka.actor.ActorLogging
import akka.actor.Props

object UserStoreActor {
  def props(userStore: DomainUserStore): Props = Props(new UserStoreActor(userStore))

  trait UserStoreRequest
  case class GetAllUsersRequest() extends UserStoreRequest
  case class GetAllUsersResponse(users: List[DomainUser])
  
  case class CreateUserRequest(user: DomainUser, password: String) extends UserStoreRequest
  case class CreateUserResponse(uid: String)
}

class UserStoreActor private[datastore] (private[this] val userStore: DomainUserStore)
    extends StoreActor with ActorLogging {

  def receive: Receive = {
    case message: GetAllUsersRequest => getAllUsers()
    case message: CreateUserRequest => createUser(message)
    case message: Any => unhandled(message)
  }
    
  def getAllUsers(): Unit = {
    mapAndReply(userStore.getAllDomainUsers(None, None, None, None)) (GetAllUsersResponse(_))
  }
  
  def createUser(message: CreateUserRequest): Unit = {
    mapAndReply (userStore.createDomainUser(message.user, Some(message.password))) (CreateUserResponse(_))
  }
}
