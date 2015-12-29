package com.convergencelabs.server.datastore

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.TryValues._
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

class ConfigurationStoreSpec
    extends PersistenceStoreSpec[ConfigurationStore]("/dbfiles/convergence.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): ConfigurationStore = new ConfigurationStore(dbPool)

  "An ConfigurationStore" when {

    "asked for a config" must {

      "return Success[ConnectionConfig] if asked for Connection Config" in withPersistenceStore { store =>
        store.getConnectionConfig().success.value shouldBe ConnectionConfig(
          5,
          5,
          10,
          10,
          5,
          60,
          10000)
      }
    }
  }
}
