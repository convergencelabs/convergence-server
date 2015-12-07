package com.convergencelabs.server.datastore

import com.convergencelabs.server.domain.DomainFqn
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import org.scalatest.WordSpecLike
import org.scalatest.Matchers

class ConfigurationStoreSpec
    extends PersistenceStoreSpec[ConfigurationStore]("/dbfiles/convergence.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): ConfigurationStore = new ConfigurationStore(dbPool)

  "An ConfigurationStore" when {

    "asked for a config" must {

      "return None if it doesn't exist" in withPersistenceStore { store =>
        store.getConfiguration("not real") shouldBe None
      }

      "return Some if it does exist" in withPersistenceStore { store =>
        // FIXME not really specific enough.
        store.getConfiguration("connection") shouldBe defined
      }

      "return Some[ConnectionConfig] if asked for Connection Config" in withPersistenceStore { store =>
        store.getConnectionConfig() shouldBe defined
      }
    }
  }
}
