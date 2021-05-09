/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain.model

import com.convergencelabs.convergence.server.backend.datastore.domain.collection.CollectionPermissionsStore
import com.convergencelabs.convergence.server.backend.datastore.domain.model.ModelPermissionsStore.modelDocToWorldPermissions
import com.convergencelabs.convergence.server.backend.datastore.domain.schema.DomainSchema.Classes
import com.convergencelabs.convergence.server.backend.datastore.{AbstractDatabasePersistence, OrientDBUtil}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.domain.collection.CollectionPermissions
import com.convergencelabs.convergence.server.model.domain.model.ModelPermissions
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.impl.ODocument
import grizzled.slf4j.Logging

import scala.util.{Success, Try}

class ModelPermissionCalculator(dbProvider: DatabaseProvider,
                                modelPermissionsStore: ModelPermissionsStore,
                                collectionPermissionsStore: CollectionPermissionsStore)
  extends AbstractDatabasePersistence(dbProvider) with Logging {

  def getUsersCurrentModelPermissions(modelId: String, userId: DomainUserId): Try[Option[ModelPermissions]] = withDb { db =>
    getModelRid(db, modelId).flatMap {
      case Some(rid) =>
        Try(rid.getRecord[ODocument]).flatMap { modelDoc =>
          resolveModelPermissions(modelId, userId, modelDoc, db).map(Some(_))
        }
      case None =>
        Success(None)
    }
  }

  private[this] def resolveModelPermissions(modelId: String, userId: DomainUserId, modelDoc: ODocument, db: ODatabaseDocument): Try[ModelPermissions] = {
    val overridesPermissions: Boolean = modelDoc.getProperty(Classes.Model.Fields.OverridePermissions)
    modelPermissionsStore.getModelUserPermissions(modelId, userId, Some(db)).flatMap {
      case Some(p) =>
        Success(p)
      case None =>
        val collectionDoc = modelDoc.getProperty("collection").asInstanceOf[ODocument]
        val collectionId = collectionDoc.getProperty("id").asInstanceOf[String]
        collectionPermissionsStore.getCollectionPermissionsForUser(collectionId, userId, Some(db)).flatMap {
          case Some(CollectionPermissions(_, read, write, remove, manage)) =>
            Success(ModelPermissions(read, write, remove, manage))
          case None =>
            if (overridesPermissions) {
              Try(modelDocToWorldPermissions(modelDoc))
            } else {
              Try(CollectionPermissionsStore.collectionDocToCollectionWorldPermissions(collectionDoc)).map { collectionWorld =>
                val CollectionPermissions(_, read, write, remove, manage) = collectionWorld
                ModelPermissions(read, write, remove, manage)
              }
            }
        }
    }
  }

  private[this] def getModelRid(db: ODatabaseDocument, modelId: String): Try[Option[ORID]] =
    OrientDBUtil.findIdentityFromSingleValueIndex(db, Classes.Model.Indices.Id, modelId)

}
