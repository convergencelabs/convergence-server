package com.convergencelabs.server.datastore

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.convergencelabs.server.domain.DomainDatabaseInfo
import com.convergencelabs.server.domain.DomainStatus
import com.convergencelabs.server.User

class DomainStoreSpec
    extends PersistenceStoreSpec[DomainStore]("/dbfiles/convergence.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): DomainStore = new DomainStore(dbPool)

  val namespace1 = "namespace1"
  val domain1 = "domain1"
  val ns1d1 = DomainFqn(namespace1, domain1)
  val ns1d1Id = "namespace1-domain1"
  val ns1d2Id = "namespace1-domain2"
  val root = "root"
  val user = User("cu0", "test", "test@convergence.com", "test", "test")

  "A DomainStore" when {

    "asked whether a domain exists" must {

      "return false if it doesn't exist" in withPersistenceStore { store =>
        store.domainExists(DomainFqn("notRealNs", "notRealId")).success.get shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { store =>
        store.domainExists(ns1d1).success.get shouldBe true
      }
    }

    "retrieving a domain config by fqn" must {

      "return None if the domain doesn't exist" in withPersistenceStore { store =>
        store.getDomainByFqn(DomainFqn("notReal", "notReal")).success.get shouldBe None
      }

      "return Some if the domain exist" in withPersistenceStore { store =>
        store.getDomainByFqn(ns1d1).success.get shouldBe defined
      }
    }

    "retrieving a domain config by id" must {
      "return None if the domain doesn't exist" in withPersistenceStore { store =>
        store.getDomainById("does not exist").success.get shouldBe None
      }

      "return Some if the domain exist" in withPersistenceStore { store =>
        store.getDomainById(ns1d1Id).success.get shouldBe defined
      }
    }

    "creating a domain" must {
      "insert the domain record into the database" in withPersistenceStore { store =>
        val dbName = "t4"
        val fqn = DomainFqn("test", "test4")
        val domain = Domain(
          "1",
          fqn,
          "Test Domain 4",
          user,
          DomainStatus.Initializing)

        val id = store.createDomain(fqn, "Test Domain 4", user.uid, DomainDatabaseInfo(dbName, root, root)).success
        store.getDomainByFqn(fqn).success.get.value shouldBe domain
        store.getDomainDatabaseInfo(fqn).success.get.value shouldBe DomainDatabaseInfo(dbName, root, root)
      }

      "return a failure if the domain exists" in withPersistenceStore { store =>
        val id = "t1"
        val domain = Domain(
          id,
          ns1d1,
          "Test Domain 1",
          user,
          DomainStatus.Initializing)

        store.createDomain(ns1d1, "Test Domain 1", user.uid, DomainDatabaseInfo(id, root, root)).success.get shouldBe DuplicateValue
      }
    }

    "getting domains by owner" must {
      "return all domains for an owner" in withPersistenceStore { store =>
        val domains = store.getDomainsByOwner("cu0").success.get
        domains.length shouldBe 3
        domains(0).id shouldBe ns1d1Id
        domains(1).id shouldBe ns1d2Id
      }
    }

    "getting domains by namespace" must {
      "return all domains for a namespace" in withPersistenceStore { store =>
        val domains = store.getDomainsInNamespace(namespace1).success.get
        domains.length shouldBe 2
        domains(0).id shouldBe ns1d1Id
        domains(1).id shouldBe ns1d2Id
      }
    }

    "removing a domain" must {
      "remove the domain record in the database if it exists" in withPersistenceStore { store =>
        store.removeDomain(ns1d1Id).success
        store.getDomainById(ns1d1Id).success.get shouldBe None
      }

      "not throw an exception if the domain does not exist" in withPersistenceStore { store =>
        store.removeDomain("doesn't exist").success
      }
    }

    "updating a domain" must {
      "sucessfully update an existing domain" in withPersistenceStore { store =>
        val toUpdate = Domain(
          "namespace1-domain1",
          DomainFqn(namespace1, domain1),
          "Test Domain 1 Updated",
          user,
          DomainStatus.Offline)

        store.updateDomain(toUpdate).success
        val queried = store.getDomainByFqn(ns1d1).success.get.value

        queried shouldBe toUpdate
      }

      "fail to update an non-existing domain" in withPersistenceStore { store =>
        val toUpdate = Domain(
          "namespace1-domain-none",
          DomainFqn(namespace1, domain1),
          "Test Domain 1 Updated",
          user,
          DomainStatus.Online)

        store.updateDomain(toUpdate).success.get shouldBe NotFound
      }
      // FIXME need to test / add updating db info.
    }
  }
}
