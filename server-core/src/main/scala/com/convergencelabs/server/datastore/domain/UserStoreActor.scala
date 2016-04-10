package com.convergencelabs.server.datastore


import akka.actor.ActorLogging
import akka.actor.Props
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.datastore.domain.DomainUserStore

object UserStoreActor {
  def props(userStore: DomainUserStore): Props = Props(new UserStoreActor(userStore))

  trait UserStoreRequest
  case object GetUsers extends UserStoreRequest
  case class GetUsersResponse(users: List[DomainUser])
  
  case class CreateUser(user: DomainUser, password: String) extends UserStoreRequest
  case class CreateUserResponse(uid: String)
}

class UserStoreActor private[datastore] (private[this] val userStore: DomainUserStore)
    extends StoreActor with ActorLogging {

  import UserStoreActor._
  
  def receive: Receive = {
    case GetUsers => getAllUsers()
    case message: CreateUser => createUser(message)
    case message: Any => unhandled(message)
  }
    
  def getAllUsers(): Unit = {
    mapAndReply(userStore.getAllDomainUsers(None, None, None, None)) (GetUsersResponse(_))
  }
  
  def createUser(message: CreateUser): Unit = {
    mapAndReply (userStore.createDomainUser(message.user, Some(message.password))) (CreateUserResponse(_))
  }
}
