package com.convergencelabs.server.datastore.domain

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.datastore.domain.ModelStoreActor.GetModelUpdateRequest
import com.convergencelabs.server.domain.{DomainUserId, DomainUserType}

import scala.util.Success

private[datastore] class ModelStoreActor(private[this] val persistenceProvider: DomainPersistenceProvider)
  extends StoreActor with ActorLogging {

  import ModelStoreActor._

  def receive: Receive = {
    case GetModels(offset, limit) =>
      handleGetModels(offset, limit)
    case GetModelsInCollection(collectionId, offset, limit) =>
      handleGetModelsInCollection(collectionId, offset, limit)
    case message: QueryModelsRequest =>
      onQueryModelsRequest(message)
    case message: GetModelUpdateRequest =>
      handleGetModelUpdate(message)
    case message: Any =>
      unhandled(message)
  }

  private[this] def handleGetModels(offset: Option[Int], limit: Option[Int]): Unit = {
    reply(persistenceProvider.modelStore.getAllModelMetaData(offset, limit))
  }

  private[this] def handleGetModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]): Unit = {
    reply(persistenceProvider.modelStore.getAllModelMetaDataInCollection(collectionId, offset, limit))
  }

  private[this] def onQueryModelsRequest(request: QueryModelsRequest): Unit = {
    val QueryModelsRequest(userId, query) = request

    val uid = Option(userId).flatMap(u => u match {
      case DomainUserType.Convergence => None
      case _ => Some(u)
    })
    reply(persistenceProvider.modelStore.queryModels(query, uid))
  }

  private[this] def handleGetModelUpdate(request: GetModelUpdateRequest): Unit = {
    val GetModelUpdateRequest(modelId, version) = request
    reply(persistenceProvider.modelStore.getModelIfNewer(modelId, version))
  }
}

object ModelStoreActor {
  def RelativePath = "ModelStoreActor"

  def props(persistenceProvider: DomainPersistenceProvider): Props =
    Props(new ModelStoreActor(persistenceProvider))

  trait ModelStoreRequest

  case class GetModels(offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest

  case class GetModelsInCollection(collectionId: String, offset: Option[Int], limit: Option[Int]) extends ModelStoreRequest

  case class QueryModelsRequest(userId: DomainUserId, query: String) extends ModelStoreRequest

  case class GetModelUpdateRequest(modelId: String, currentVersion: Long) extends ModelStoreRequest

}
