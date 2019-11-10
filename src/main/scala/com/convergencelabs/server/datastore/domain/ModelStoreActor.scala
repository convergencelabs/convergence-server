package com.convergencelabs.server.datastore.domain

import akka.actor.{ActorLogging, Props}
import com.convergencelabs.server.datastore.StoreActor
import com.convergencelabs.server.domain.model.Model
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
    val GetModelUpdateRequest(modelId, currentVersion, currentPermissions, userId) = request
    val result = persistenceProvider.modelPermissionsStore.getUsersCurrentModelPermissions(modelId, userId).flatMap {
      case Some(permissions) =>
        if (permissions.read) {
          // Model still exists and is still readable. Check to see if the
          // permissions or model need to be updated.
          val permissionsUpdate = if (permissions == currentPermissions) {
            None
          } else {
            Some(permissions)
          }

          persistenceProvider.modelStore.getModelIfNewer(modelId, currentVersion) map { modelUpdate =>
            (permissionsUpdate, modelUpdate) match {
              case (None, None) =>
                // No update to permissions or model.
                OfflineModelNotUpdate()
              case (p, m) =>
                // At least one is different.
                OfflineModelUpdated(m,p)
            }
          }
        } else {
          // This means the permissions were changed and now this
          // user does not have read permissions. We don't even
          // bother to look up the model.
          Success(OfflineModelPermissionRevoked())
        }
      case None =>
        // Model doesn't exist anymore
        Success(OfflineModelDeleted())
    }

    reply(result)
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


  case class GetModelUpdateRequest(modelId: String,
                                   currentVersion: Long,
                                   currentPermissions: ModelPermissions,
                                   userId: DomainUserId) extends ModelStoreRequest


  sealed trait OfflineModelUpdateAction

  case class OfflineModelPermissionRevoked() extends OfflineModelUpdateAction

  case class OfflineModelNotUpdate() extends OfflineModelUpdateAction

  case class OfflineModelDeleted() extends OfflineModelUpdateAction

  case class OfflineModelUpdated(model: Option[Model], permissions: Option[ModelPermissions]) extends OfflineModelUpdateAction

}
