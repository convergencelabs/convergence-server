package com.convergencelabs.server.domain.model

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.UnauthorizedException
import com.convergencelabs.server.domain.model.data.ObjectValue
import java.util.UUID

case class CollectionAutoCreateDisabled(message: String) extends Exception(message)
case class NoCreatePermissions(message: String) extends Exception(message)

class ModelCreator {
  
  def generateModelId(): String= UUID.randomUUID().toString

  def createModel(
    persistenceProvider: DomainPersistenceProvider,
    username: Option[String],
    collectionId: String,
    modelId: String,
    data: ObjectValue,
    overridePermissions: Option[Boolean],
    worldPermissions: Option[ModelPermissions],
    userPermissions: Option[Map[String, ModelPermissions]]): Try[Model] = {

    verifyCanCreate(collectionId, username, persistenceProvider) flatMap { _ =>
      persistenceProvider.collectionStore.ensureCollectionExists(collectionId)
    } flatMap { _ =>
      val overrideWorld = overridePermissions.getOrElse(false)
      val worldPerms = worldPermissions.getOrElse(ModelPermissions(false, false, false, false))
      val model = persistenceProvider.modelStore.createModel(modelId, collectionId, data, overrideWorld, worldPerms)
      model
    } flatMap { model =>
      val ModelMetaData(model.metaData.collectionId, model.metaData.modelId, version, created, modified, overworldPermissions, worldPermissions, model.metaData.valuePrefix) = model.metaData
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

  def verifyCanCreate(collectionId: String, username: Option[String], persistenceProvider: DomainPersistenceProvider): Try[Unit] = {
    persistenceProvider.collectionStore.collectionExists(collectionId) flatMap { exists =>
      if (exists) {
        username match {
          case Some(user) =>
            persistenceProvider.modelPermissionsStore.getCollectionUserPermissions(collectionId, user).flatMap { userPermissions =>
              userPermissions match {
                case Some(p) =>
                  Success(p)
                case None =>
                  persistenceProvider.modelPermissionsStore.getCollectionWorldPermissions(collectionId)
              }
            } flatMap { permissions =>
              if (permissions.create) {
                Success(())
              } else {
                val message = s"Can not auto create model because the user does not have permissions to create models in the specified collection: ${collectionId}";
                Failure(UnauthorizedException(message))
              }
            }
          case None =>
            Success(())
        }
      } else {
        // Eventually we need some sort of domain wide configuration to allow / disallow auto creation of
        // collections.
        if (true) {
          Success(())
        } else {
          Failure(CollectionAutoCreateDisabled(s"Can not create model, because the collection %{collectionId} does not exist and auto creation of collections is disabled."))
        }
      }
    }
  }
}
