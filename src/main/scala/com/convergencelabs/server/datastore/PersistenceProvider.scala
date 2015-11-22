package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import scala.util.Try
import scala.util.Success
import scala.util.Failure

class PersistenceProvider(dbPool: OPartitionedDatabasePool) {
  val convergenceConfigStore = new ConfigurationStore(dbPool)
  val domainStore = new DomainStore(dbPool)
  
  def validateConnection(): Boolean = {
    Try[Unit](dbPool.acquire().close()) match {
      case Success(x) => true
      case Failure(x) => false
    }
  }
}