package com.convergencelabs.server.datastore.domain

import org.json4s.JsonAST.JValue
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

class DomainPersistenceProvider(private[domain] val dbPool: OPartitionedDatabasePool) {

  val modelStore = new ModelStore(dbPool)

  val modelHistoryStore = new ModelHistoryStore(dbPool)

  val modelSnapshotStore = new ModelSnapshotStore(dbPool)

  val userStore = new DomainUserStore(dbPool)
  
  val userCredentialStore = new DomainUserCredentialStore(dbPool)
}