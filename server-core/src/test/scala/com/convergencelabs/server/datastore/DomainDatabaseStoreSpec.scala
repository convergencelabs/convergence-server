package com.convergencelabs.server.datastore

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DomainDatabaseStoreSpec.SpecStores
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainFqn

object DomainDatabaseStoreSpec {
  case class SpecStores(domain: DomainStore, domainDatabase: DomainDatabaseStore)
}

class DomainDatabaseStoreSpec
    extends PersistenceStoreSpec[SpecStores](DeltaCategory.Convergence)
    with WordSpecLike
    with Matchers {

  def createStore(dbProvider: DatabaseProvider): SpecStores = {
    SpecStores(new DomainStore(dbProvider), new DomainDatabaseStore(dbProvider))
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
