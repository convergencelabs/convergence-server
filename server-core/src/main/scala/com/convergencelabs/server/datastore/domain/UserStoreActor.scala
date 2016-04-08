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

object UserStoreActor {
  def props(domainFqn: DomainFqn): Props = Props(new UserStoreActor(domainFqn))

  trait UserStoreRequest
  case class CreateUserRequest(user: DomainUser, password: String) extends UserStoreRequest

}

class UserStoreActor private[datastore] (private[this] val domainFqn: DomainFqn)
    extends Actor with ActorLogging {

  
  private[this] var persistenceProvider: DomainPersistenceProvider = _
  
  def receive: Receive = {
    case message: CreateUserRequest => createUser(message)
    case message: Any => unhandled(message)
  }
    
  def createUser(message: CreateUserRequest): Try[Unit] = {
    ???
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

