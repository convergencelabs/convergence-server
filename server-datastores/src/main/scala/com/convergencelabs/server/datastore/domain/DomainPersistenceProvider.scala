package com.convergencelabs.server.datastore.domain

import com.convergencelabs.server.datastore.AbstractPersistenceProvider
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

class DomainPersistenceProvider(private[this] val dbPool: OPartitionedDatabasePool) extends AbstractPersistenceProvider(dbPool) {

  val configStore = new DomainConfigStore(dbPool)

  val userStore = new DomainUserStore(dbPool)

  val jwtAuthKeyStore = new JwtAuthKeyStore(dbPool)

  val modelOperationStore = new ModelOperationStore(dbPool)

  val modelSnapshotStore = new ModelSnapshotStore(dbPool)

  val modelStore = new ModelStore(dbPool, modelOperationStore, modelSnapshotStore)

  val collectionStore = new CollectionStore(dbPool, modelStore: ModelStore)

  val modelOperationProcessor = new ModelOperationProcessor(dbPool)

}
