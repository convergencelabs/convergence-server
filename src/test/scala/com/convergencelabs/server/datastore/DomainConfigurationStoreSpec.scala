package com.convergencelabs.server.datastore

import org.scalatest.WordSpecLike
import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.common.log.OLogManager
import com.convergencelabs.server.domain.DomainFqn
import scala.util.Try
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.model.SnapshotConfig
import java.time.temporal.ChronoUnit
import java.time.Duration
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import org.scalatest.Matchers
import org.scalatest.OptionValues._

class DomainConfigurationStoreSpec
    extends PersistenceStoreSpec[DomainConfigurationStore]("/dbfiles/convergence.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): DomainConfigurationStore = new DomainConfigurationStore(dbPool)

  val snapshotConfig = SnapshotConfig(
    false,
    true,
    true,
    250,
    500,
    false,
    false,
    Duration.of(0, ChronoUnit.MINUTES),
    Duration.of(0, ChronoUnit.MINUTES))

  "A DomainConfigurationStore" when {

    "asked whether a domain exists" must {

      "return false if it doesn't exist" in withPersistenceStore { store =>
        store.domainExists(DomainFqn("notReal", "notReal")) shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { store =>
        store.domainExists(DomainFqn("test", "test1")) shouldBe true
      }
    }

    "retrieving a domain config by fqn" must {

      "return None if the domain doesn't exist" in withPersistenceStore { store =>
        store.getDomainConfigByFqn(DomainFqn("notReal", "notReal")) shouldBe None
      }

      "return Some if the domain exist" in withPersistenceStore { store =>
        store.getDomainConfigByFqn(DomainFqn("test", "test1")) shouldBe defined
      }
    }

    "retrieving a domain config by id" must {
      "return None if the domain doesn't exist" in withPersistenceStore { store =>
        store.getDomainConfigById("does not exist") shouldBe None
      }

      "return Some if the domain exist" in withPersistenceStore { store =>
        store.getDomainConfigById("t1") shouldBe defined
      }
    }

    "creating a domain" must {
      "insert the domain record into the database" in withPersistenceStore { store =>
        val domainConfig = DomainConfig(
          "t4",
          DomainFqn("test", "test4"),
          "Test Domain 4",
          "root",
          "root",
          Map(),
          TokenKeyPair("private", "public"),
          snapshotConfig)
          
        store.createDomainConfig(domainConfig)
        
        store.getDomainConfigByFqn(DomainFqn("test", "test4")).value shouldBe domainConfig
      }

      "throw an exception if the domain exists" in withPersistenceStore { store =>
        val domainConfig = DomainConfig(
          "t1",
          DomainFqn("test", "test1"),
          "Test Domain 1",
          "root",
          "root",
          Map(),
          TokenKeyPair("private", "public"),
          snapshotConfig)

        // FIXME better exception
        intercept[Throwable] {
          val createTry = store.createDomainConfig(domainConfig)
        }
      }
    }

    "getting domains by namespace" must {
      "return all domains for a namespace" in withPersistenceStore { store =>
        store.getDomainConfigsInNamespace("test").length shouldBe 3
      }
    }

    "removing a domain" must {
      "remove the domain record in the database if it exists" in withPersistenceStore { store =>
        store.removeDomainConfig("t1")
        store.getDomainConfigById("t1") shouldBe None
      }

      "not throw an exception if the domain does not exist" in withPersistenceStore { store =>
        store.removeDomainConfig("doesn't exist")
      }
    }

    "retrieving a domain key by domainFqn and keyId" must {

      "return None if the domain doesn't exist" in withPersistenceStore { store =>
        store.getDomainKey(DomainFqn("doesn't exist", "doesn't exist"), "doesn't exit") shouldBe None
      }

      "return None if the key doesn't exist" in withPersistenceStore { store =>
        store.getDomainKey(DomainFqn("test", "test1"), "doesn't exit") shouldBe None
      }

      "return Some if the key exist" in withPersistenceStore { store =>
        store.getDomainKey(DomainFqn("test", "test1"), "test") shouldBe defined
      }
    }

    "retrieving domain keys by domainFqn" must {

      "return None if the domain doesn't exist" in withPersistenceStore { store =>
        store.getDomainKeys(DomainFqn("doesn't exist", "doesn't exist")) shouldBe None
      }

      "return a list of keys if the domain exists" in withPersistenceStore { store =>
        store.getDomainKeys(DomainFqn("test", "test1")) shouldBe defined
      }
    }
  }
}