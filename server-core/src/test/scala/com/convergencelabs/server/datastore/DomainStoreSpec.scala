package com.convergencelabs.server.datastore

import scala.util.Try

import org.scalatest.Matchers
import org.scalatest.OptionValues._
import org.scalatest.TryValues._
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException

class DomainStoreSpec
    extends PersistenceStoreSpec[DomainStore]("/dbfiles/convergence.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): DomainStore = new DomainStore(dbPool)

  "A DomainStore" when {

    "asked whether a domain exists" must {

      "return false if it doesn't exist" in withPersistenceStore { store =>
        store.domainExists(DomainFqn("notReal", "notReal")).success.get shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { store =>
        store.domainExists(DomainFqn("test", "test1")).success.get shouldBe true
      }
    }

    "retrieving a domain config by fqn" must {

      "return None if the domain doesn't exist" in withPersistenceStore { store =>
        store.getDomainByFqn(DomainFqn("notReal", "notReal")).success.get shouldBe None
      }

      "return Some if the domain exist" in withPersistenceStore { store =>
        store.getDomainByFqn(DomainFqn("test", "test1")).success.get shouldBe defined
      }
    }

    "retrieving a domain config by id" must {
      "return None if the domain doesn't exist" in withPersistenceStore { store =>
        store.getDomainById("does not exist").success.get shouldBe None
      }

      "return Some if the domain exist" in withPersistenceStore { store =>
        store.getDomainById("t1").success.get shouldBe defined
      }
    }

    "creating a domain" must {
      "insert the domain record into the database" in withPersistenceStore { store =>
        val fqn = DomainFqn("test", "test4")
        val domainConfig = Domain(
          "t4",
          fqn,
          "Test Domain 4",
          "root",
          "root")

        store.createDomain(domainConfig).success
        store.getDomainByFqn(fqn).success.get shouldBe domainConfig
      }

      "return a failure if the domain exists" in withPersistenceStore { store =>
        val domainConfig = Domain(
          "t1",
          DomainFqn("test", "test1"),
          "Test Domain 1",
          "root",
          "root")

          store.createDomain(domainConfig).failed.get shouldBe a[ORecordDuplicatedException]
      }
    }

    "getting domains by namespace" must {
      "return all domains for a namespace" in withPersistenceStore { store =>
        store.getDomainsInNamespace("test").success.get.length shouldBe 3
      }
    }

    "removing a domain" must {
      "remove the domain record in the database if it exists" in withPersistenceStore { store =>
        store.removeDomain("t1").success
        store.getDomainById("t1").success.get shouldBe None
      }

      "not throw an exception if the domain does not exist" in withPersistenceStore { store =>
        store.removeDomain("doesn't exist").success
      }
    }
  }
}