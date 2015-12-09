package com.convergencelabs.server.datastore.domain

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

class DomainConfigStoreSpec
    extends PersistenceStoreSpec[DomainConfigStore]("/dbfiles/domain.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): DomainConfigStore = new DomainConfigStore(dbPool)

  "A DomainConfigStore" when {
    "retrieving domain keys by domainFqn" must {
      "return the correct list of keys" in withPersistenceStore { store =>
        store.getTokenKeys().success.get // FIXME
      }
    }
  }
}
