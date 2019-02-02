package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.DomainUserType

case class QueryModelsRequest(userId: DomainUserId, query: String)
case class QueryOrderBy(field: String, ascending: Boolean)
case class QueryModelsResponse(result: List[ModelQueryResult])

object ModelLookupActor {

  val RelativePath = "modelLookupManager"

  def props(domainFqn: DomainFqn,
    persistenceManager: DomainPersistenceManager): Props = Props(
    new ModelLookupActor(
      domainFqn,
      persistenceManager))
}

class ModelLookupActor(
  private[this] val domainFqn: DomainFqn,
  private[this] val persistenceManager: DomainPersistenceManager)
    extends Actor with ActorLogging {

  var persistenceProvider: DomainPersistenceProvider = _

  def receive: Receive = {
    case message: QueryModelsRequest => onQueryModelsRequest(message)
    case message: Any => unhandled(message)
  }

  private[this] def onQueryModelsRequest(request: QueryModelsRequest): Unit = {
    val QueryModelsRequest(userId, query) = request
    
    val uid = Option(userId).flatMap( u => u match {
      case DomainUserType.Convergence => None
      case _ => Some(u)
    })
    persistenceProvider.modelStore.queryModels(query, uid) map { result =>
      sender ! QueryModelsResponse(result)
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  override def postStop(): Unit = {
    log.debug("ModelQueryManagerActor({}) shutting down.", this.domainFqn)
    persistenceManager.releasePersistenceProvider(self, context, domainFqn)
  }

  override def preStart(): Unit = {
    persistenceManager.acquirePersistenceProvider(self, context, domainFqn) match {
      case Success(provider) =>
        persistenceProvider = provider
      case Failure(cause) =>
        throw new IllegalStateException("Could not obtain a persistence provider", cause)
    }
  }
}
