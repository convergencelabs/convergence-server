package com.convergencelabs.server.db.schema

import java.time.Instant
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import org.scalatest.BeforeAndAfterEach
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.convergencelabs.server.domain.datastore.ConvergenceDeltaHistory
import com.convergencelabs.server.domain.datastore.ConvergenceDelta
import com.convergencelabs.server.datastore.DeltaHistoryStore
import com.convergencelabs.server.datastore.DomainStore
import org.scalatest.Finders

class DeltaHistoryStoreSpec extends WordSpecLike with Matchers with BeforeAndAfterEach {

  var dbCounter = 1
  val dbName = getClass.getSimpleName

  var db: ODatabaseDocumentTx = null
  var pool: OPartitionedDatabasePool = null
  var deltaHistoryStore: DeltaHistoryStore = null
  var domainStore: DomainStore = null

  override def beforeEach() {
    val uri = s"memory:${dbName}${dbCounter}"
    dbCounter += 1
    db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()
    db.create()

    pool = new OPartitionedDatabasePool(uri, "admin", "admin")
    val schemaManager = new DatabaseSchemaManager(pool, DeltaCategory.Convergence, true)
    schemaManager.installLatest()

    deltaHistoryStore = new DeltaHistoryStore(pool)
    domainStore = new DomainStore(pool)
  }

  override def afterEach() {
    pool.close()
    pool = null
    db.activateOnCurrentThread()
    db.drop()
    db = null
  }

  "The DeltaHistoryStore" when {
    "retrieving a convergence delta history record" must {
      "match the record that was saved" in {
        val currrentTime = Instant.now()
        val delta = ConvergenceDelta(1, "Some YAML")
        val deltaHistory = ConvergenceDeltaHistory(delta, DeltaHistoryStore.Status.Success, None, currrentTime)
        deltaHistoryStore.saveConvergenceDeltaHistory(deltaHistory)

        val retrievedDeltaHistory = deltaHistoryStore.getConvergenceDeltaHistory(1)
        retrievedDeltaHistory.get.get shouldEqual deltaHistory
      }
    }

    "retrieving a convergence delta history record" must {
      "match the record that was saved" in {
        val currrentTime = Instant.now()
        val delta = ConvergenceDelta(1, "Some YAML")
        val deltaHistory = ConvergenceDeltaHistory(delta, DeltaHistoryStore.Status.Success, None, currrentTime)
        deltaHistoryStore.saveConvergenceDeltaHistory(deltaHistory)

        val retrievedDeltaHistory = deltaHistoryStore.getConvergenceDeltaHistory(1)
        retrievedDeltaHistory.get.get shouldEqual deltaHistory
      }
    }
  }
}
