package com.convergencelabs.server.domain.model

import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ModelPermissions

class ModelPermissionResolver() {
  def getModelUserPermissions(id: String, sk: SessionKey, persistenceProvider: DomainPersistenceProvider): Try[ModelPermissions] = {
    if (sk.admin) {
      Success(ModelPermissions(true, true, true, true))
    } else {
      val permissionsStore = persistenceProvider.modelPermissionsStore
      permissionsStore.modelOverridesCollectionPermissions(id).flatMap { overrides =>
        if (overrides) {
          permissionsStore.getModelUserPermissions(id, sk.uid).flatMap { userPerms =>
            userPerms match {
              case Some(p) =>
                Success(p)
              case None =>
                permissionsStore.getModelWorldPermissions(id)
            }
          }
        } else {
          permissionsStore.getCollectionWorldPermissionsForModel(id).flatMap { collectionPerms =>
            val CollectionPermissions(create, read, write, remove, manage) = collectionPerms
            Success(ModelPermissions(read, write, remove, manage))
          }
        }
      }
    }
  }

  def getModelPermissions(modelId: String, collectionId: String, persistenceProvider: DomainPersistenceProvider): Try[RealTimeModelPermissions] = {
    for {
      overrideCollection <- persistenceProvider.modelPermissionsStore.modelOverridesCollectionPermissions(modelId)
      collectionWorld <- persistenceProvider.modelPermissionsStore.getCollectionWorldPermissions(collectionId)
      collectionUsers <- persistenceProvider.modelPermissionsStore.getAllCollectionUserPermissions(collectionId)
      modelWorld <- persistenceProvider.modelPermissionsStore.getModelWorldPermissions(modelId)
      modelUsers <- persistenceProvider.modelPermissionsStore.getAllModelUserPermissions(modelId)
    } yield {
      RealTimeModelPermissions(
        overrideCollection,
        collectionWorld,
        collectionUsers,
        modelWorld,
        modelUsers)
    }
  }
}
