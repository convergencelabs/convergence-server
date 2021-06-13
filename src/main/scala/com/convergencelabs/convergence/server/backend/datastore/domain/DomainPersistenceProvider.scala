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

package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.backend.datastore.AbstractPersistenceProvider
import com.convergencelabs.convergence.server.backend.datastore.domain.activity.ActivityStore
import com.convergencelabs.convergence.server.backend.datastore.domain.chat.ChatStore
import com.convergencelabs.convergence.server.backend.datastore.domain.collection.{CollectionPermissionsStore, CollectionStore}
import com.convergencelabs.convergence.server.backend.datastore.domain.config.DomainConfigStore
import com.convergencelabs.convergence.server.backend.datastore.domain.group.UserGroupStore
import com.convergencelabs.convergence.server.backend.datastore.domain.jwt.JwtAuthKeyStore
import com.convergencelabs.convergence.server.backend.datastore.domain.model._
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.PermissionsStore
import com.convergencelabs.convergence.server.backend.datastore.domain.session.SessionStore
import com.convergencelabs.convergence.server.backend.datastore.domain.user.DomainUserStore
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.DomainId

import scala.util.Try

trait DomainPersistenceProvider {
  val domainId: DomainId

  val dbProvider: DatabaseProvider

  val configStore: DomainConfigStore

  val userStore: DomainUserStore

  val userGroupStore: UserGroupStore

  val sessionStore: SessionStore

  val jwtAuthKeyStore: JwtAuthKeyStore

  val activityStore: ActivityStore

  val modelOperationStore: ModelOperationStore

  val modelSnapshotStore: ModelSnapshotStore

  val modelStore: ModelStore

  val collectionStore: CollectionStore

  val collectionPermissionsStore: CollectionPermissionsStore

  val modelOperationProcessor: ModelOperationProcessor

  val modelPermissionsStore: ModelPermissionsStore

  val modelPermissionCalculator: ModelPermissionCalculator

  val chatStore: ChatStore

  val permissionsStore: PermissionsStore

  val domainStateProvider: DomainStateProvider

  def validateConnection(): Try[Unit]

  def shutdown(): Unit
}

class DomainPersistenceProviderImpl(val domainId: DomainId,
                                    val dbProvider: DatabaseProvider,
                                    val domainStateProvider: DomainStateProvider)
    extends AbstractPersistenceProvider(dbProvider)
    with DomainPersistenceProvider {

  val configStore = new DomainConfigStore(dbProvider)

  val userStore = new DomainUserStore(dbProvider)

  val userGroupStore = new UserGroupStore(dbProvider)

  val sessionStore = new SessionStore(dbProvider)

  val jwtAuthKeyStore = new JwtAuthKeyStore(dbProvider)

  val activityStore = new ActivityStore(dbProvider)

  val modelOperationStore = new ModelOperationStore(dbProvider)

  val modelSnapshotStore = new ModelSnapshotStore(dbProvider)

  val modelStore = new ModelStore(dbProvider, modelOperationStore, modelSnapshotStore)

  val collectionStore = new CollectionStore(dbProvider)

  val collectionPermissionsStore = new CollectionPermissionsStore(dbProvider)

  val modelOperationProcessor = new ModelOperationProcessor(dbProvider, modelOperationStore, modelStore)

  val modelPermissionsStore = new ModelPermissionsStore(dbProvider)

  val modelPermissionCalculator = new ModelPermissionCalculator(dbProvider, modelPermissionsStore, collectionPermissionsStore)

  val chatStore = new ChatStore(dbProvider)

  val permissionsStore = new PermissionsStore(dbProvider)
}
