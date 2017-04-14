package com.convergencelabs.server.domain.model

import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.model.data.ObjectValue
import scala.util.Success
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore
import com.convergencelabs.server.datastore.domain.CollectionPermissions

object ModelCreator {
  def createModel(
    persistenceProvider: DomainPersistenceProvider,
    username: Option[String],
    collectionId: String,
    modelId: Option[String],
    data: ObjectValue,
    overridePermissions: Option[Boolean],
    worldPermissions: Option[ModelPermissions],
    userPermissions: Option[Map[String, ModelPermissions]]): Try[Model] = {
    persistenceProvider.collectionStore.ensureCollectionExists(collectionId) flatMap { _ =>
      val overrideWorld = overridePermissions.getOrElse(false)
      val worldPerms = worldPermissions.getOrElse(ModelPermissions(false, false, false, false))
      val model = persistenceProvider.modelStore.createModel(collectionId, modelId, data, overrideWorld, worldPerms)
      model
    } flatMap { model =>
      val ModelMetaData(model.metaData.collectionId, model.metaData.modelId, version, created, modified, overworldPermissions, worldPermissions) = model.metaData
      val snapshot = ModelSnapshot(ModelSnapshotMetaData(model.metaData.modelId, version, created), model.data)
      persistenceProvider.modelSnapshotStore.createSnapshot(snapshot) flatMap { _ =>
        username match {
          case Some(uname) =>
            persistenceProvider
              .modelPermissionsStore
              .updateModelUserPermissions(
                model.metaData.modelId,
                uname,
                ModelPermissions(true, true, true, true)) map (_ => model)
          case None =>
            Success(())
        }
      } flatMap { _ =>
        val userPerms = userPermissions.getOrElse(Map()).map { entry => (entry._1 -> Some(entry._2)) }
        persistenceProvider
          .modelPermissionsStore
          .updateAllModelUserPermissions(model.metaData.modelId, userPerms)
          .map(_ => model)
      }
    }
  }

  def canCreate(collectionId: String, sk: SessionKey, permissionsStore: ModelPermissionsStore): Try[Boolean] = {
    if (sk.admin) {
      Success(true)
    } else {
      // Eventually we need some sort of domain wide configuration to allow / disallow auto creation of
      // collections.
      permissionsStore.getCollectionUserPermissions(collectionId, sk.uid).flatMap { userPermissions =>
        userPermissions match {
          case Some(p) =>
            Success(p)
          case None =>
            permissionsStore.getCollectionWorldPermissions(collectionId)
        }
      } map (c => c.create)
    }
  }
}
