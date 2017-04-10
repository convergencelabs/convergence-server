package com.convergencelabs.server.domain.model

import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.model.data.ObjectValue
import scala.util.Success

object ModelCreator {
  def createModel(
    persistenceProvider: DomainPersistenceProvider,
    username: Option[String],
    collectionId: String,
    modelId: Option[String],
    data: ObjectValue,
    overridePermissions: Option[Boolean],
    worldPermissions: Option[ModelPermissions]): Try[Model] = {
    persistenceProvider.collectionStore.ensureCollectionExists(collectionId) flatMap { _ =>
      val overrideWorld = overridePermissions.getOrElse(false)
      val worldPerms = worldPermissions.getOrElse(ModelPermissions(false, false, false, false))
      val model = persistenceProvider.modelStore.createModel(
        collectionId, modelId, data, overrideWorld, worldPerms)
      model
    } flatMap { model =>
      val ModelMetaData(fqn, version, created, modified, overworldPermissions, worldPermissions) = model.metaData
      val snapshot = ModelSnapshot(ModelSnapshotMetaData(fqn, version, created), model.data)
      persistenceProvider.modelSnapshotStore.createSnapshot(snapshot) flatMap { _ =>
        username match {
          case Some(uname) =>
            persistenceProvider
              .modelPermissionsStore
              .updateModelUserPermissions(
                model.metaData.fqn,
                uname,
                ModelPermissions(true, true, true, true)) map (_ => model)
          case None =>
            Success(model)
        } 
      }
    }
  }
}