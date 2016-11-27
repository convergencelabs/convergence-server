package com.convergencelabs.server.datastore

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.DomainStatus
import com.convergencelabs.server.User
import com.convergencelabs.server.datastore.DomainDatabaseStoreSpec.SpecStores

object DomainDatabaseStoreSpec {
  case class SpecStores(domain: DomainStore, domainDatabase: DomainDatabaseStore)
}

class DomainDatabaseStoreSpec
    extends PersistenceStoreSpec[SpecStores]("/convergence.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): SpecStores = {
    SpecStores(new DomainStore(dbPool), new DomainDatabaseStore(dbPool))
  }

  val namespace1 = "namespace1"
  
  val domain1 = "domain1"
  val domain2 = "domain2"
  val domain3 = "domain3"
  
  val ns1d1 = DomainFqn(namespace1, domain1)
  val ns1d2 = DomainFqn(namespace1, domain2)
  
  // Not in database
  val ns1d3 = DomainFqn(namespace1, domain3)
  
  val root = "root"
  
  val DbAdminUsername = "admin"
  val DbAdminPassword = "admin"
  val DbNormalUsername = "writer"
  val DbNormalPassword = "writer"
  

  "A DomainDatabaseStore" when {

    "asked whether a domain exists" must {

      "return false if it doesn't exist" in withPersistenceStore { stores =>
      }
    }
  }
}
