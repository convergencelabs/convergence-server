package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.datastore.AbstractPersistenceProvider
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.datastore.DatabaseProvider

class DomainPersistenceProvider(val dbProvider: DatabaseProvider) extends AbstractPersistenceProvider(dbProvider) {

  val configStore = new DomainConfigStore(dbProvider)

  val userStore = new DomainUserStore(dbProvider)
  
  val sessionStore = new SessionStore(dbProvider)

  val jwtAuthKeyStore = new JwtAuthKeyStore(dbProvider)

  val modelOperationStore = new ModelOperationStore(dbProvider)

  val modelSnapshotStore = new ModelSnapshotStore(dbProvider)

  val modelStore = new ModelStore(dbProvider, modelOperationStore, modelSnapshotStore)

  val collectionStore = new CollectionStore(dbProvider, modelStore: ModelStore)

  val modelOperationProcessor = new ModelOperationProcessor(dbProvider, modelOperationStore)
}
