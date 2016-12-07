package com.convergencelabs.server.datastore

import java.time.Duration

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainStatus

object DomainStoreSpec {
  case class SpecStores(user: UserStore, domain: DomainStore)
}

class DomainStoreSpec
    extends PersistenceStoreSpec[DomainStoreSpec.SpecStores](DeltaCategory.Convergence)
    with WordSpecLike
    with Matchers {

  def createStore(dbProvider: DatabaseProvider): DomainStoreSpec.SpecStores = {
    DomainStoreSpec.SpecStores(new UserStore(dbProvider, Duration.ofSeconds(1)), new DomainStore(dbProvider))
  }

  val namespace1 = "namespace1"
  
  val domain1 = "domain1"
  val domain2 = "domain2"
  val domain3 = "domain3"
  
  val ns1d1 = DomainFqn(namespace1, domain1)
  val ns1d2 = DomainFqn(namespace1, domain2)
  val ns1d3 = DomainFqn(namespace1, domain3)
  
  val ns2d1 = DomainFqn("namespace2", "domain1")
  
  val Username = "test"
  val Owner = User(Username, "email", "first", "last", "display")
  val NotOwner = User("other", "otherEmail", "first", "last", "display")
  val Password = "password"
  
  "A DomainStore" when {

    "asked whether a domain exists" must {
      "return false if it doesn't exist" in withTestData { stores =>
        stores.domain.domainExists(DomainFqn("notRealNs", "notRealId")).get shouldBe false
      }

      "return true if it does exist" in withTestData { stores =>
        stores.domain.createDomain(ns1d1, "", Username).get
        stores.domain.domainExists(ns1d1).get shouldBe true
      }
    }

    "retrieving a domain by fqn" must {
      "return None if the domain doesn't exist" in withTestData { stores =>
        stores.domain.createDomain(ns1d1, "", Username).get
        stores.domain.getDomainByFqn(DomainFqn("notReal", "notReal")).get shouldBe None
      }

      "return Some if the domain exist" in withTestData { stores =>
        stores.domain.createDomain(ns1d1, "", Username).get
        stores.domain.getDomainByFqn(ns1d1).success.get shouldBe defined
      }
    }

    "creating a domain" must {
      "insert the domain correct record into the database" in withTestData { stores =>
        val dbName = "t4"
        val fqn = DomainFqn("test", "test4")
        val domain = Domain(
          fqn,
          "Test Domain 4",
          Username,
          DomainStatus.Initializing,
          "")

        stores.domain.createDomain(fqn, "Test Domain 4", Username).get
        stores.domain.getDomainByFqn(fqn).get.value shouldBe domain
      }

      "return a DuplicateValueExcpetion if the domain exists" in withTestData { stores =>
        stores.domain.createDomain(ns1d1, "", Username)
        stores.domain.createDomain(ns1d1, "Test Domain 1", Username).failure.exception shouldBe a[DuplicateValueExcpetion]
      }
      
      "fail if the owner does not exist" in withTestData { stores =>
        stores.domain.createDomain(ns1d1, "Test Domain 1", "no user").failure
      }
    }

    "getting domains by owner" must {
      "return all domains for an owner" in withTestData { stores =>
        stores.domain.createDomain(ns1d1, "", Username).get
        stores.domain.createDomain(ns1d2, "", Username).get
        
        stores.user.createUser(NotOwner, Password).get
        stores.domain.createDomain(ns1d3, "", NotOwner.username).get
        
        val domains = stores.domain.getDomainsByOwner(Username).get
        domains.map { x => x.domainFqn }.toSet shouldBe Set(ns1d1, ns1d2) 
      }
    }

    "getting domains by namespace" must {
      "return all domains for a namespace" in withTestData { stores =>
        stores.domain.createDomain(ns1d1, "", Username).get
        stores.domain.createDomain(ns1d2, "", Username).get
        stores.domain.createDomain(ns2d1, "", Username).get
        
        val domains = stores.domain.getDomainsInNamespace(namespace1).get
        domains.map { x => x.domainFqn }.toSet shouldBe Set(ns1d1, ns1d2)
      }
    }

    "removing a domain" must {
      "remove the domain record in the database if it exists" in withTestData { stores =>
        stores.domain.createDomain(ns1d1, "", Username).get
        stores.domain.createDomain(ns1d2, "", Username).get
        stores.domain.removeDomain(ns1d1).get
        stores.domain.domainExists(ns1d1).get shouldBe false
        stores.domain.domainExists(ns1d2).get shouldBe true
      }

      "not throw an exception if the domain does not exist" in withTestData { stores =>
        stores.domain.removeDomain(ns1d3).failure.exception shouldBe a[EntityNotFoundException]
      }
    }

    "updating a domain" must {
      "sucessfully update an existing domain" in withTestData { stores =>
        stores.domain.createDomain(ns1d1, "", Username).get
        
        val toUpdate = Domain(
          DomainFqn(namespace1, domain1),
          "Test Domain 1 Updated",
          Username,
          DomainStatus.Offline,
          "domain manually take offline by user")

        stores.domain.updateDomain(toUpdate).get
        val queried = stores.domain.getDomainByFqn(ns1d1).get.value

        queried shouldBe toUpdate
      }

      "fail to update an non-existing domain" in withTestData { stores =>
        val toUpdate = Domain(
          DomainFqn(namespace1, domain3),
          "Test Domain 1 Updated",
          Username,
          DomainStatus.Online,
          "")

        stores.domain.updateDomain(toUpdate).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
  }
  
  def withTestData(testCode: DomainStoreSpec.SpecStores => Any): Unit = {
    this.withPersistenceStore { stores =>
      stores.user.createUser(Owner, Password).get
      testCode(stores)
    }
  }
}
