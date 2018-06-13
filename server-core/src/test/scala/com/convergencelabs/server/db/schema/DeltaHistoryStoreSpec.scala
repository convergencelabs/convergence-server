package com.convergencelabs.server.db.schema

import java.time.Duration
import java.time.Instant

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.convergence.ConvergenceDelta
import com.convergencelabs.server.datastore.convergence.ConvergenceDeltaHistory
import com.convergencelabs.server.db.DatabaseProvider
import com.convergenconvergence.celabs.server.datastore.DeltaHistoryStore
import com.convergencelabs.server.datastore.convergence.DomainDelta
import com.convergencelabs.server.datastore.convergence.DomainDeltaHistconvergence.ory
import com.convergencelabs.server.datastore.DomainStore
import com.convergencelabs.server.datastore.UserStore
import com.convergencelabs.server.datastore.UserStore.User
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.domain.DomainFqn

object DeltaHistoryStoreSpec {
  case class SpecStores(user: UserStore, delta: DeltaHistoryStore, domain: DomainStore)
}

class DeltaHistoryStoreSpec
    extends PersistenceStoreSpec[DeltaHistoryStoreSpec.SpecStores](DeltaCategory.Convergence)
    with WordSpecLike
    with Matchers {

  def createStore(dbProvider: DatabaseProvider): DeltaHistoryStoreSpec.SpecStores = {
    DeltaHistoryStoreSpec.SpecStores(
      new UserStore(dbProvider, Duration.ofSeconds(1)),
      new DeltaHistoryStore(dbProvider),
      new DomainStore(dbProvider))
  }

  "A DeltaHistoryStore" when {
    "retrieving a convergence delta history record" must {
      "match the record that was saved" in withPersistenceStore { stores =>
        val currrentTime = Instant.now()
        val delta = ConvergenceDelta(1, "Some YAML")
        val deltaHistory = ConvergenceDeltaHistory(delta, DeltaHistoryStore.Status.Success, None, currrentTime)
        stores.delta.saveConvergenceDeltaHistory(deltaHistory)

        val retrievedDeltaHistory = stores.delta.getConvergenceDeltaHistory(1).get
        retrievedDeltaHistory.value shouldEqual deltaHistory
      }
    }

    "creating a DomainDeltaHistory entry" must {
      "get the record that was saved" in withDomainTestData { stores =>
        val currrentTime = Instant.now()
        val delta = DomainDelta(1, "Some YAML")
        val deltaHistory = DomainDeltaHistory(ns1d1, delta, DeltaHistoryStore.Status.Success, None, currrentTime)
        stores.delta.saveDomainDeltaHistory(deltaHistory)

        val retrievedDeltaHistory = stores.delta.getDomainDeltaHistory(ns1d1, 1).get
        retrievedDeltaHistory.value shouldEqual deltaHistory
      }
    }
    
    "deleting DomainDeltaHistory entries for a domain" must {
      "delete all and only the ones for that domain" in withDomainTestData { stores =>
        val currrentTime = Instant.now()
        val d1h1 = DomainDeltaHistory(ns1d1, DomainDelta(1, "1"), DeltaHistoryStore.Status.Success, None, currrentTime)
        stores.delta.saveDomainDeltaHistory(d1h1)
        
        val d1h2 = DomainDeltaHistory(ns1d1, DomainDelta(2, "2"), DeltaHistoryStore.Status.Success, None, currrentTime)
        stores.delta.saveDomainDeltaHistory(d1h2)

        val d2h1 = DomainDeltaHistory(ns1d2, DomainDelta(1, "1"), DeltaHistoryStore.Status.Success, None, currrentTime)
        stores.delta.saveDomainDeltaHistory(d2h1)
        
        stores.delta.getDomainDeltaHistory(ns1d1, 1).get.value shouldBe d1h1
        stores.delta.getDomainDeltaHistory(ns1d1, 2).get.value shouldBe d1h2
        stores.delta.getDomainDeltaHistory(ns1d2, 1).get.value shouldBe d2h1
        
        stores.delta.removeDeltaHistoryForDomain(ns1d1).get
        
        stores.delta.getDomainDeltaHistory(ns1d1, 1).get shouldBe None
        stores.delta.getDomainDeltaHistory(ns1d1, 2).get shouldBe None
        stores.delta.getDomainDeltaHistory(ns1d2, 1).get.value shouldBe d2h1
      }
    }
  }

  val Username = "test"
  val Owner = User(Username, "email", "first", "last", "display")
  val Password = "password"
  
  val ns1d1 = DomainFqn("ns1", "d1")
  val ns1d2 = DomainFqn("ns1", "d2")
  val ns2d1 = DomainFqn("ns2", "d1")

  def withDomainTestData(testCode: DeltaHistoryStoreSpec.SpecStores => Any): Unit = {
    this.withPersistenceStore { stores =>
      stores.user.createUser(Owner, Password).get
      stores.domain.createDomain(ns1d1, "ns1d1", Username).get
      stores.domain.createDomain(ns1d2, "ns1d2", Username).get
      stores.domain.createDomain(ns2d1, "ns2d1", Username).get
      testCode(stores)
    }
  }
}
