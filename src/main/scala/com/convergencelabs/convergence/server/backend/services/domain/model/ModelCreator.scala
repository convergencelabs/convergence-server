/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.model

import com.convergencelabs.convergence.server.backend.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.convergence.server.backend.datastore.domain.collection.CollectionPermissionsStore
import com.convergencelabs.convergence.server.backend.services.domain.UnauthorizedException
import com.convergencelabs.convergence.server.model.domain.model._
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId

import java.time.Instant
import scala.util.{Failure, Success, Try}

/**
 * This exception indicates that the caller asked to create a model in a
 * collection that does not exist, and that the domain has auto creation
 * of collections disabled.
 *
 * @param message A human readable message describing the error.
 */
final case class CollectionAutoCreateDisabledException(message: String) extends Exception(message)

/**
 * This exception indicates that the user does not have the required
 * permissions to create the model in the collection they indicated.
 *
 * @param message A human readable message describing the error.
 */
final case class NoCreatePermissionsException(message: String) extends Exception(message)

/**
 * The model creator class is a helper class that orchestrates creating
 * realtime models.  Creating models is a multi-step process involving
 * checking multiple permissions and creating multiple entries in
 * various data stores. This class provides a single method that
 * orchestrates these steps.
 */
private[model] class ModelCreator {

  /**
   * The default permissions to assign to the creator of the model.
   */
  private val creatorPermissions = ModelPermissions(read = true, write = true, remove = true, manage = true)

  /**
   * Creates a new model in the system.
   *
   * @param persistenceProvider The persistence provider to use for all data
   *                            persistence interaction.
   * @param creatorUserId       The id of the user that is creating the model
   *                            or None, if none should be recorded.
   * @param collectionId        The id of the collection in which to create
   *                            the model.
   * @param modelId             The id of the model to create.
   * @param data                The data to initialize the model with.
   * @param createdTime         The time the model should be recorded as being
   *                            created at.
   * @param overrideWorld       Whether the model should override the
   *                            collection's world permissions.
   * @param worldPermissions    The model specific world permissions.
   * @param userPermissions     The user permissions to create the model with.
   *
   * @return A reference to the created model.
   */
  def createModel(persistenceProvider: DomainPersistenceProvider,
                  creatorUserId: Option[DomainUserId],
                  collectionId: String,
                  modelId: String,
                  data: ObjectValue,
                  createdTime: Option[Instant],
                  overrideWorld: Option[Boolean],
                  worldPermissions: Option[ModelPermissions],
                  userPermissions: Map[DomainUserId, ModelPermissions]): Try[Model] = {
    for {
      _ <- verifyCanCreate(collectionId, creatorUserId, persistenceProvider)
      _ <- persistenceProvider.collectionStore.ensureCollectionExists(collectionId)
      model <- createModel(persistenceProvider, collectionId, modelId, data, createdTime, overrideWorld, worldPermissions)
      _ <- createModelSnapshot(persistenceProvider, model)
      _ <- setCreatorPermissions(persistenceProvider, creatorUserId, model)
      _ <- createUserPermissions(persistenceProvider, userPermissions, model)
    } yield model
  }

  private def verifyCanCreate(collectionId: String, userId: Option[DomainUserId], persistenceProvider: DomainPersistenceProvider): Try[Unit] = {
    persistenceProvider.collectionStore.collectionExists(collectionId) flatMap {
      case true =>
        userId match {
          case Some(uid) =>
            validateUserPermissionsForCollection(collectionId, persistenceProvider.collectionPermissionsStore, uid)
          case None =>
            Success(())
        }
      case false =>
        validateCollectionAutoCreateEnabled(collectionId, persistenceProvider)
    }
  }

  private def validateUserPermissionsForCollection(collectionId: String, collectionPermissionsStore: CollectionPermissionsStore, uid: DomainUserId) = {
    collectionPermissionsStore.getCollectionPermissionsForUser(collectionId, uid).flatMap {
      case Some(p) =>
        Success(p)
      case None =>
        collectionPermissionsStore.getCollectionWorldPermissions(collectionId)
    } flatMap { permissions =>
      if (permissions.create) {
        Success(())
      } else {
        val message = s"Cannot create the model because the user does not have permissions to create models in the specified collection: $collectionId"
        Failure(UnauthorizedException(message))
      }
    }
  }

  private def validateCollectionAutoCreateEnabled(collectionId: String, persistenceProvider: DomainPersistenceProvider) = {
    persistenceProvider.configStore.getCollectionConfig().flatMap { config =>
      if (config.autoCreate) {
        Success(())
      } else {
        Failure(CollectionAutoCreateDisabledException(s"Can not create model, because the collection '$collectionId' does not exist and auto creation of collections is disabled."))
      }
    }
  }

  private def createModel(persistenceProvider: DomainPersistenceProvider, collectionId: String, modelId: String, data: ObjectValue, createdTime: Option[Instant], overrideWorld: Option[Boolean], worldPermissions: Option[ModelPermissions]) = {
    val ow = overrideWorld.getOrElse(false)
    val worldPerms = worldPermissions.getOrElse(ModelPermissions(read = false, write = false, remove = false, manage = false))
    val model = persistenceProvider.modelStore.createModel(modelId, collectionId, data, createdTime, ow, worldPerms)
    model
  }

  private def createModelSnapshot(persistenceProvider: DomainPersistenceProvider, model: Model) = {
    val ModelMetaData(model.metaData.id, model.metaData.collection, version, created, _, _, _, model.metaData.valuePrefix) = model.metaData
    val snapshot = ModelSnapshot(ModelSnapshotMetaData(model.metaData.id, version, created), model.data)
    persistenceProvider.modelSnapshotStore.createSnapshot(snapshot)
  }

  private def createUserPermissions(persistenceProvider: DomainPersistenceProvider, userPermissions: Map[DomainUserId, ModelPermissions], model: Model) = {
    val userPerms = userPermissions.transform((_, v) => Some(v))
    persistenceProvider.modelPermissionsStore
      .updateAllModelUserPermissions(model.metaData.id, userPerms)
  }

  private def setCreatorPermissions(persistenceProvider: DomainPersistenceProvider,
                                    creatorUserId: Option[DomainUserId],
                                    model: Model): Try[Unit] = {
    creatorUserId match {
      case Some(uid) =>
        persistenceProvider.modelPermissionsStore
          .updateModelUserPermissions(model.metaData.id, uid, creatorPermissions)
      case None =>
        Success(())
    }
  }
}
