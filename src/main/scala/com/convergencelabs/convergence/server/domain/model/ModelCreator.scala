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

package com.convergencelabs.convergence.server.domain.model

import java.util.UUID

import com.convergencelabs.convergence.server.datastore.domain.{DomainPersistenceProvider, ModelPermissions}
import com.convergencelabs.convergence.server.domain.{DomainUserId, UnauthorizedException}
import com.convergencelabs.convergence.server.domain.model.data.ObjectValue

import scala.util.{Failure, Success, Try}

case class CollectionAutoCreateDisabled(message: String) extends Exception(message)

case class NoCreatePermissions(message: String) extends Exception(message)

class ModelCreator {

  def generateModelId(): String = UUID.randomUUID().toString

  def createModel(
                   persistenceProvider: DomainPersistenceProvider,
                   creatorUserId: Option[DomainUserId],
                   collectionId: String,
                   modelId: String,
                   data: ObjectValue,
                   overrideWorld: Option[Boolean],
                   worldPermissions: Option[ModelPermissions],
                   userPermissions: Map[DomainUserId, ModelPermissions]): Try[Model] = {

    verifyCanCreate(collectionId, creatorUserId, persistenceProvider) flatMap { _ =>
      persistenceProvider.collectionStore.ensureCollectionExists(collectionId)
    } flatMap { _ =>
      val ow = overrideWorld.getOrElse(false)
      val worldPerms = worldPermissions.getOrElse(ModelPermissions(read = false, write = false, remove = false, manage = false))
      val model = persistenceProvider.modelStore.createModel(modelId, collectionId, data, ow, worldPerms)
      model
    } flatMap { model =>
      val ModelMetaData(model.metaData.id, model.metaData.collection, version, created, _, _, _, model.metaData.valuePrefix) = model.metaData
      val snapshot = ModelSnapshot(ModelSnapshotMetaData(model.metaData.id, version, created), model.data)
      persistenceProvider.modelSnapshotStore.createSnapshot(snapshot) flatMap { _ =>
        creatorUserId match {
          case Some(uid) =>
            persistenceProvider
              .modelPermissionsStore
              .updateModelUserPermissions(
                model.metaData.id,
                uid,
                ModelPermissions(read = true, write = true, remove = true, manage = true)) map (_ => model)
          case None =>
            Success(())
        }
      } flatMap { _ =>
        val userPerms = userPermissions.mapValues(Some(_))
        persistenceProvider
          .modelPermissionsStore
          .updateAllModelUserPermissions(model.metaData.id, userPerms)
          .map(_ => model)
      }
    }
  }

  def verifyCanCreate(collectionId: String, userId: Option[DomainUserId], persistenceProvider: DomainPersistenceProvider): Try[Unit] = {
    persistenceProvider.collectionStore.collectionExists(collectionId) flatMap { exists =>
      if (exists) {
        userId match {
          case Some(uid) =>
            persistenceProvider.modelPermissionsStore.getCollectionUserPermissions(collectionId, uid).flatMap {
              case Some(p) =>
                Success(p)
              case None =>
                persistenceProvider.modelPermissionsStore.getCollectionWorldPermissions(collectionId)
            } flatMap { permissions =>
              if (permissions.create) {
                Success(())
              } else {
                val message = s"Cannot create the model because the user does not have permissions to create models in the specified collection: $collectionId"
                Failure(UnauthorizedException(message))
              }
            }
          case None =>
            Success(())
        }
      } else {
        // TODO Eventually we need some sort of domain wide configuration to allow / disallow auto creation of
        //  collections.
        if (true) {
          Success(())
        } else {
          Failure(CollectionAutoCreateDisabled(s"Can not create model, because the collection $collectionId does not exist and auto creation of collections is disabled."))
        }
      }
    }
  }
}
