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

package com.convergencelabs.convergence.server.backend.datastore.domain.user

import com.convergencelabs.convergence.server.backend.datastore.domain.chat.ChatStore
import com.convergencelabs.convergence.server.backend.datastore.domain.collection.CollectionPermissionsStore
import com.convergencelabs.convergence.server.backend.datastore.domain.group.UserGroupStore
import com.convergencelabs.convergence.server.backend.datastore.domain.model.ModelPermissionsStore
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.PermissionsStore
import com.convergencelabs.convergence.server.model.domain.user.{DomainUserId, DomainUserType}

import scala.util.Try

/**
 * A simple helper class that orchestrates deleting a user. It ensures that the user
 * is removed from all relevant stores.
 */
class DomainUserDeletionOrchestrator(private val domainUserStore: DomainUserStore,
                                     private val userGroupStore: UserGroupStore,
                                     private val chatStore: ChatStore,
                                     private val permissionsStore: PermissionsStore,
                                     private val modelPermissionsStore: ModelPermissionsStore,
                                     private val collectionPermissionsStore: CollectionPermissionsStore
                                    ) {

  /**
   * Deletes a normal user from all relevant places in the datastore.
   * @param username The username of the normal user to remove.
   * @return A Try indicating success or failure.
   */
  def deleteNormalDomainUser(username: String): Try[Unit] = {
    val userId = DomainUserId(DomainUserType.Normal, username)
    for {
      _ <- permissionsStore.removeAllPermissionsForUser(userId)
      _ <- modelPermissionsStore.removeAllModelPermissionsForUser(userId)
      _ <- collectionPermissionsStore.removeAllCollectionPermissionsForUser(userId)
      _ <- userGroupStore.removeUserFromAllGroups(userId)
      _ <- chatStore.removeUserFromAllChats(userId)
      _ <- domainUserStore.deleteNormalDomainUser(username)
    } yield ()
  }
}
