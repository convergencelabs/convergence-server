package com.convergencelabs.server.datastore.domain

import org.scalatest.WordSpec
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.BeforeAndAfterAll
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.common.log.OLogManager
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JNull
import org.json4s.JsonAST.JInt

class ModelSnapshotStoreSpec extends WordSpec {

  OLogManager.instance().setConsoleLevel("WARNING")

  "A ModelSnapshotStore" when {

    "when creating a snapshot" must {

      "be able to get the snapshot that was created" in withModelSnapshotStore { store =>
        val fqn = ModelFqn("collection", "model")
        val version = 2L
        val timestamp = 4L

        val created = SnapshotData(
          SnapshotMetaData(fqn, version, timestamp),
          JObject("key" -> JNull))
          
        store.createSnapshot(created)

        val queried = store.getSnapshot(fqn, version)
        assert(queried.get == created)
      }
    }
  }

  // NOTE: This uses the "loan" method of scalatest fixtures
  var dbCounter = 0
  def withModelSnapshotStore(testCode: ModelSnapshotStore => Any) {
    // make sure no accidental collisions
    val uri = "memory:ModelSnapshotStoreSpec" + dbCounter
    dbCounter += 1

    val db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()
    db.create()

    val file = getClass.getResource("/dbfiles/t1.gz").getFile()
    val dbImport = new ODatabaseImport(db, file, CommandListener)
    dbImport.importDatabase()
    dbImport.close()
    
    val dbPool = new OPartitionedDatabasePool(uri, "admin", "admin")
    val store = new ModelSnapshotStore(dbPool)

    try {
      testCode(store)
    } finally {
      db.activateOnCurrentThread()
      db.drop() // Drop will close and drop
    }
  }

  object CommandListener extends OCommandOutputListener() {
    def onMessage(iText: String) = {
    }
  }
}