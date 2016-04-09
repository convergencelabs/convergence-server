package com.convergencelabs.server.datastore

import scala.util.Try
import akka.actor.Actor
import akka.actor.ActorLogging
import scala.util.Success
import scala.util.Failure
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.typesafe.config.Config
import com.convergencelabs.server.domain.DomainFqn
import akka.actor.Props
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.datastore.UserStoreActor._
import com.convergencelabs.server.domain.DomainUser

object UserStoreActor {
  def props(domainFqn: DomainFqn): Props = Props(new UserStoreActor(domainFqn))

  trait UserStoreRequest
  case class GetAllUsersRequest() extends UserStoreRequest
  case class GetAllUsersResponse(users: List[DomainUser])
  
  case class CreateUserRequest(user: DomainUser, password: String) extends UserStoreRequest
  case class CreateUserResponse(uid: String)

}

class UserStoreActor private[datastore] (private[this] val domainFqn: DomainFqn)
    extends Actor with ActorLogging {

  
  private[this] var persistenceProvider: DomainPersistenceProvider = _
  
  def receive: Receive = {
    case message: GetAllUsersRequest => getAllUsers()
    case message: CreateUserRequest => createUser(message)
    case message: Any => unhandled(message)
  }
    
  def getAllUsers(): Try[GetAllUsersResponse] = {
    persistenceProvider.userStore.getAllDomainUsers(None, None, None, None) map (GetAllUsersResponse(_))
  }
  
  def createUser(message: CreateUserRequest): Try[CreateUserResponse] = {
    persistenceProvider.userStore.createDomainUser(message.user, Some(message.password)) map (CreateUserResponse(_))
  }
  
  override def preStart(): Unit = {
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) match {
      case Success(provider) => persistenceProvider = provider
      case Failure(cause) => {
        log.error(cause, "Unable to obtain a domain persistence provider.")
      }
    }
  }

  override def postStop(): Unit = {
    log.debug(s"Domain(${domainFqn}) received shutdown command.  Shutting down.")
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }
}

