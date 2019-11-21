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

import com.convergencelabs.convergence.server.datastore.domain.{CollectionPermissions, ModelPermissions}
import com.convergencelabs.convergence.server.domain.DomainUserId

/**
 * The RealTimeModelPermissions class encodes the current permissions for a
 * model and provides a helper method to resolve the permissions of a
 * particular user based on the various configurations. The precedent is as
 * follows:
 *
 *   1. A user specific permission for the model.
 *   2. A user specific permission at the collection level.
 *   3. World permissions for the model IF the model overrides the
 *      collection world permissions.
 *   4. The World permissions for the collection.
 *
 * @param overrideCollection Whether the model world permissions override the
 *                           collection world permissions.
 * @param collectionWorld    The collection world permissions that apply to all
 *                           models in the collection that don't override them.
 * @param collectionUsers    Users with specific permissions to the collection.
 * @param modelWorld         The model world permissions.
 * @param modelUsers         User specific permissions.
 */
case class RealTimeModelPermissions(overrideCollection: Boolean,
                                    collectionWorld: CollectionPermissions,
                                    collectionUsers: Map[DomainUserId, CollectionPermissions],
                                    modelWorld: ModelPermissions,
                                    modelUsers: Map[DomainUserId, ModelPermissions]) {

  def resolveSessionPermissions(userId: DomainUserId): ModelPermissions = {
    if (userId.isConvergence) {
      ModelPermissions(read = true, write = true, remove = true, manage = true)
    } else {
      modelUsers.getOrElse(userId,
        collectionUsers
          .get(userId)
          .map(toModelPermissions)
          .getOrElse(
            if (overrideCollection) {
              modelWorld
            } else {
              toModelPermissions(collectionWorld)
            }
          )
      )
    }
  }

  private[this] def toModelPermissions(collectionPermissions: CollectionPermissions): ModelPermissions = {
    val CollectionPermissions(_, read, write, remove, manage) = collectionPermissions
    ModelPermissions(read, write, remove, manage)
  }
}
