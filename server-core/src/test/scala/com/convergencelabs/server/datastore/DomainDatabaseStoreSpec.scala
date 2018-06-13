package com.convergencelabs.server.datastore

import java.time.Duration

import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.DomainDatabaseStoreSpec.SpecStores
import com.convergencelabs.server.datastore.UserStore.User
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.convergence.DomainDatabaseStore
import com.convergencelabs.server.datastore.convergence.DomainStore

object DomainDatabaseStoreSpec {
  case class SpecStores(
      user: UserStore,
      domain: DomainStore, 
      domainDatabase: DomainDatabaseStore)
}

class DomainDatabaseStoreSpec
    extends PersistenceStoreSpec[SpecStores](DeltaCategory.Convergence)
    with WordSpecLike
    with Matchers {

  def createStore(dbProvider: DatabaseProvider): SpecStores = {
    SpecStores(new UserStore(dbProvider, Duration.ofMinutes(1)), new DomainStore(dbProvider), new DomainDatabaseStore(dbProvider))
  }

  val namespace1 = "namespace1"
  
  val domain1 = "domain1"
  val domain2 = "domain2"
  val domain3 = "domain3"
  
  val ns1d1 = DomainFqn(namespace1, domain1)
  val ns1d2 = DomainFqn(namespace1, domain2)
  
  // Not in database
  val ns2d1 = DomainFqn("ns2", "d1")
  
  val owner = User("owner", "foo@example.com", "", "", "") 
  val notOwner = User("notOwner", "not@example.com", "", "", "") 
  
  val adminUsername = "admin"
  val adminPassword = "admin"
  val username = "writer"
  val password = "writer"
  
  val db1 = "db1"
  
  "A DomainDatabaseStore" when {

    "creating a domain database entry" must {
      "successfully create and retrieve the entry" in withTestData { stores =>
        val database = DomainDatabase(ns1d1, db1, username, password, adminUsername, adminPassword)
        stores.domainDatabase.createDomainDatabase(database).get
        val queried = stores.domainDatabase.getDomainDatabase(ns1d1).get.get
        queried shouldBe database
      }
      
      "disallow duplicate entries for the same domain" in withTestData { stores =>
        val database = DomainDatabase(ns1d1, db1, username, password, adminUsername, adminPassword)
        stores.domainDatabase.createDomainDatabase(database).get
        stores.domainDatabase.createDomainDatabase(database).failure.exception shouldBe a[DuplicateValueException]
      }
    }
  }
  
  def withTestData(testCode: SpecStores => Any): Unit = {
    withPersistenceStore { stores =>
      stores.user.createUser(owner, "password").get
      stores.domain.createDomain(ns1d1, "ns1d1", owner.username).get
      stores.domain.createDomain(ns1d2, "ns1d2", owner.username).get
      
      stores.user.createUser(notOwner, "password").get
      stores.domain.createDomain(ns2d1, "ns2d1", notOwner.username).get
      
      testCode(stores)
    }
  }
}
