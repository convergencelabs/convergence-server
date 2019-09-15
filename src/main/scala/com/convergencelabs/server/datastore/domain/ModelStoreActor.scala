package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.model.ModelQueryResult

import ModelStoreActor.GetModels
import ModelStoreActor.GetModelsInCollection
import akka.actor.ActorLogging
import akka.actor.Props

object ModelStoreActor {
  def RelativePath = "ModelStoreActor"

  def props(
    persistenceProvider: DomainPersistenceProvider): Props =
    Props(new ModelStoreActor(persistenceProvider))

  trait ModelStoreRequest
  case class GetModels(offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
  case class GetModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest
  case class QueryModelsRequest(userId: DomainUserId, query: String) extends ModelStoreRequest
}

class ModelStoreActor private[datastore] (private[this] val persistenceProvider: DomainPersistenceProvider)
  extends StoreActor with ActorLogging {

  import ModelStoreActor._

  def receive: Receive = {
    case GetModels(offset, limit) =>
      getModels(offset, limit)
    case GetModelsInCollection(collectionId, offset, limit) =>
      getModelsInCollection(collectionId, offset, limit)
    case message: QueryModelsRequest =>
      onQueryModelsRequest(message)
    case message: Any =>
      unhandled(message)
  }

  def getModels(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(persistenceProvider.modelStore.getAllModelMetaData(offset, limit))
  }

  def getModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]): Unit = {
    reply(persistenceProvider.modelStore.getAllModelMetaDataInCollection(collectionId, offset, limit))
  }
  
  private[this] def onQueryModelsRequest(request: QueryModelsRequest): Unit = {
    val QueryModelsRequest(userId, query) = request
    
    val uid = Option(userId).flatMap( u => u match {
      case DomainUserType.Convergence => None
      case _ => Some(u)
    })
    reply(persistenceProvider.modelStore.queryModels(query, uid)) 
  }
}
