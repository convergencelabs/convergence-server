package com.convergencelabs.server.db.schema

import java.time.Instant

import org.scalatest.BeforeAndAfterEach
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.ConvergenceDelta
import com.convergencelabs.server.datastore.ConvergenceDeltaHistory
import com.convergencelabs.server.datastore.DatabaseProvider
import com.convergencelabs.server.datastore.DeltaHistoryStore
import com.convergencelabs.server.datastore.DomainStore
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx

class DeltaHistoryStoreSpec extends WordSpecLike with Matchers with BeforeAndAfterEach {

  var dbCounter = 1
  val dbName = getClass.getSimpleName

  var db: ODatabaseDocumentTx = null
  var dbProvider: DatabaseProvider = null
  var deltaHistoryStore: DeltaHistoryStore = null
  var domainStore: DomainStore = null

  override def beforeEach() {
    val uri = s"memory:${dbName}${dbCounter}"
    dbCounter += 1
    db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()
    db.create()

    dbProvider = DatabaseProvider(db)
    val schemaManager = new TestingSchemaManager(db, DeltaCategory.Convergence, true)
    schemaManager.install()

    deltaHistoryStore = new DeltaHistoryStore(dbProvider)
    domainStore = new DomainStore(dbProvider)
  }

  override def afterEach() {
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
