package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.domain.DomainPersistenceManager

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status

case class QueryModelsRequest(sk: SessionKey, query: String)
case class QueryOrderBy(field: String, ascending: Boolean)
case class QueryModelsResponse(result: List[ModelQueryResult])

object ModelQueryManagerActor {

  val RelativePath = "modelManager"

  def props(domainFqn: DomainFqn,
    persistenceManager: DomainPersistenceManager,
    modelPermissionResolver: ModelPermissionResolver,
    modelCreator: ModelCreator): Props = Props(
    new ModelQueryManagerActor(
      domainFqn,
      persistenceManager))
}

class ModelQueryManagerActor(
  private[this] val domainFqn: DomainFqn,
  private[this] val persistenceManager: DomainPersistenceManager)
    extends Actor with ActorLogging {

  var persistenceProvider: DomainPersistenceProvider = _

  def receive: Receive = {
    case message: QueryModelsRequest => onQueryModelsRequest(message)
    case message: Any => unhandled(message)
  }

  private[this] def onQueryModelsRequest(request: QueryModelsRequest): Unit = {
    val QueryModelsRequest(sk, query) = request
    val username = request.sk.admin match {
      case true => None
      case false => Some(request.sk.uid)
    }
    persistenceProvider.modelStore.queryModels(query, username) map { result =>
      sender ! QueryModelsResponse(result)
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  override def postStop(): Unit = {
    log.debug("ModelManagerActor({}) received shutdown command.  Shutting down all Realtime Models.", this.domainFqn)
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
