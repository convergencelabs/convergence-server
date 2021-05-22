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
import com.convergencelabs.convergence.server.model.domain.model.ModelPermissions
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId

import scala.util.{Failure, Success, Try}

private[model] class ModelPermissionResolver() {

  def getModelUserPermissions(modelId: String, userId: DomainUserId, persistenceProvider: DomainPersistenceProvider): Try[ModelPermissions] = {
    if (userId.isConvergence) {
      Success(ModelPermissions(read = true, write = true, remove = true, manage = true))
    } else {
      val permissionsStore = persistenceProvider.modelPermissionCalculator
      permissionsStore.getUsersCurrentModelPermissions(modelId, userId) flatMap {
        case Some(p) => Success(p)
        case None => Failure(ModelNotFoundException(modelId))
      }
    }
  }

  def getModelAndCollectionPermissions(modelId: String, collectionId: String, persistenceProvider: DomainPersistenceProvider): Try[RealtimeModelPermissions] = {
    for {
      overrideCollection <- persistenceProvider.modelPermissionsStore.modelOverridesCollectionPermissions(modelId)
      collectionWorld <- persistenceProvider.collectionPermissionsStore.getCollectionWorldPermissions(collectionId)
      collectionUsers <- persistenceProvider.collectionPermissionsStore.getUserPermissionsForCollection(collectionId)
      modelWorld <- persistenceProvider.modelPermissionsStore.getModelWorldPermissions(modelId)
      modelUsers <- persistenceProvider.modelPermissionsStore.getAllModelUserPermissions(modelId)
    } yield {
      RealtimeModelPermissions(
        overrideCollection,
        collectionWorld,
        collectionUsers,
        modelWorld,
        modelUsers)
    }
  }

  def getModelPermissions(modelId: String, persistenceProvider: DomainPersistenceProvider): Try[ResolvedModelPermission] = {
    for {
      overrideCollection <- persistenceProvider.modelPermissionsStore.modelOverridesCollectionPermissions(modelId)
      modelWorld <- persistenceProvider.modelPermissionsStore.getModelWorldPermissions(modelId)
      modelUsers <- persistenceProvider.modelPermissionsStore.getAllModelUserPermissions(modelId)
    } yield {
      ResolvedModelPermission(
        overrideCollection,
        modelWorld,
        modelUsers)
    }
  }
}
