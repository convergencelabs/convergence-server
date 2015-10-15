package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

class PersistenceProvider(dbPool: OPartitionedDatabasePool) {
  val convergenceConfigStore = new ConfigurationStore(dbPool)
  val domainConfigStore = new DomainConfigurationStore(dbPool)
}