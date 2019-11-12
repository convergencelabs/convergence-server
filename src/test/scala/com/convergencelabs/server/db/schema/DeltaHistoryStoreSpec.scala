/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db.schema

import java.time.Duration
import java.time.Instant

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.convergence.ConvergenceDelta
import com.convergencelabs.server.datastore.convergence.ConvergenceDeltaHistory
import com.convergencelabs.server.datastore.convergence.DeltaHistoryStore
import com.convergencelabs.server.datastore.convergence.DomainDelta
import com.convergencelabs.server.datastore.convergence.DomainDeltaHistory
import com.convergencelabs.server.datastore.convergence.DomainStore
import com.convergencelabs.server.datastore.convergence.UserStore
import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.domain.DomainId
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.datastore.convergence.NamespaceStore

object DeltaHistoryStoreSpec {
  case class SpecStores(user: UserStore, delta: DeltaHistoryStore, domain: DomainStore, namespace: NamespaceStore)
}

class DeltaHistoryStoreSpec
  extends PersistenceStoreSpec[DeltaHistoryStoreSpec.SpecStores](DeltaCategory.Convergence)
  with WordSpecLike
  with Matchers {

  def createStore(dbProvider: DatabaseProvider): DeltaHistoryStoreSpec.SpecStores = {
    DeltaHistoryStoreSpec.SpecStores(
      new UserStore(dbProvider),
      new DeltaHistoryStore(dbProvider),
      new DomainStore(dbProvider),
      new NamespaceStore(dbProvider))
  }

  "A DeltaHistoryStore" when {
    "retrieving a convergence delta history record" must {
      "match the record that was saved" in withPersistenceStore { stores =>
        val currentTime = truncatedInstantNow
        val delta = ConvergenceDelta(1, "Some YAML")
        val deltaHistory = ConvergenceDeltaHistory(delta, DeltaHistoryStore.Status.Success, None, currentTime)
        stores.delta.saveConvergenceDeltaHistory(deltaHistory)

        val retrievedDeltaHistory = stores.delta.getConvergenceDeltaHistory(1).get
        retrievedDeltaHistory.value shouldEqual deltaHistory
      }
    }

    "creating a DomainDeltaHistory entry" must {
      "get the record that was saved" in withDomainTestData { stores =>
        val currentTime = truncatedInstantNow
        val delta = DomainDelta(1, "Some YAML")
        val deltaHistory = DomainDeltaHistory(ns1d1, delta, DeltaHistoryStore.Status.Success, None, currentTime)
        stores.delta.saveDomainDeltaHistory(deltaHistory)

        val retrievedDeltaHistory = stores.delta.getDomainDeltaHistory(ns1d1, 1).get
        retrievedDeltaHistory.value shouldEqual deltaHistory
      }
    }

    "deleting DomainDeltaHistory entries for a domain" must {
      "delete all and only the ones for that domain" in withDomainTestData { stores =>
        val currentTime = truncatedInstantNow
        val d1h1 = DomainDeltaHistory(ns1d1, DomainDelta(1, "1"), DeltaHistoryStore.Status.Success, None, currentTime)
        stores.delta.saveDomainDeltaHistory(d1h1)

        val d1h2 = DomainDeltaHistory(ns1d1, DomainDelta(2, "2"), DeltaHistoryStore.Status.Success, None, currentTime)
        stores.delta.saveDomainDeltaHistory(d1h2)

        val d2h1 = DomainDeltaHistory(ns1d2, DomainDelta(1, "1"), DeltaHistoryStore.Status.Success, None, currentTime)
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
  val Owner = User(Username, "email", "first", "last", "display", None)
  val Password = "password"
  val BearerToken = "token"

  val ns1d1 = DomainId("ns1", "d1")
  val ns1d2 = DomainId("ns1", "d2")
  val ns2d1 = DomainId("ns2", "d1")

  val DummyDomainDatabase = DomainDatabase("11", "", "", "", "")
  val DummyDomainDatabase12 = DomainDatabase("12", "", "", "", "")
  val DummyDomainDatabase21 = DomainDatabase("21", "", "", "", "")

  def withDomainTestData(testCode: DeltaHistoryStoreSpec.SpecStores => Any): Unit = {
    this.withPersistenceStore { stores =>
      stores.user.createUser(Owner, Password, BearerToken).get

      stores.namespace.createNamespace("ns1", "Namespace 1", false)
      stores.namespace.createNamespace("ns2", "Namespace 2", false)

      stores.domain.createDomain(ns1d1, "ns1d1", DummyDomainDatabase).get
      stores.domain.createDomain(ns1d2, "ns1d2", DummyDomainDatabase12).get
      stores.domain.createDomain(ns2d1, "ns2d1", DummyDomainDatabase21).get
      testCode(stores)
    }
  }
}
