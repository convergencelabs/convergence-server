/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.datastore.AbstractPersistenceProvider
import com.convergencelabs.server.db.DatabaseProvider
import scala.util.Try

trait DomainPersistenceProvider {
  val dbProvider: DatabaseProvider
  
  val configStore: DomainConfigStore

  val userStore: DomainUserStore

  val userGroupStore: UserGroupStore

  val sessionStore: SessionStore

  val jwtAuthKeyStore: JwtAuthKeyStore

  val modelOperationStore: ModelOperationStore

  val modelSnapshotStore: ModelSnapshotStore

  val modelStore: ModelStore

  val collectionStore: CollectionStore

  val modelOperationProcessor: ModelOperationProcessor

  val modelPermissionsStore: ModelPermissionsStore

  val chatStore: ChatStore

  val permissionsStore: PermissionsStore
  
  def validateConnection(): Try[Unit];

  def shutdown(): Unit;
}

class DomainPersistenceProviderImpl(val dbProvider: DatabaseProvider)
    extends AbstractPersistenceProvider(dbProvider)
    with DomainPersistenceProvider {

  val configStore = new DomainConfigStore(dbProvider)

  val userStore = new DomainUserStore(dbProvider)

  val userGroupStore = new UserGroupStore(dbProvider)

  val sessionStore = new SessionStore(dbProvider)

  val jwtAuthKeyStore = new JwtAuthKeyStore(dbProvider)

  val modelOperationStore = new ModelOperationStore(dbProvider)

  val modelSnapshotStore = new ModelSnapshotStore(dbProvider)

  val modelStore = new ModelStore(dbProvider, modelOperationStore, modelSnapshotStore)

  val collectionStore = new CollectionStore(dbProvider, modelStore: ModelStore)

  val modelOperationProcessor = new ModelOperationProcessor(dbProvider, modelOperationStore, modelStore)

  val modelPermissionsStore = new ModelPermissionsStore(dbProvider)

  val chatStore = new ChatStore(dbProvider)

  val permissionsStore = new PermissionsStore(dbProvider)
}
