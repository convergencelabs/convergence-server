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

  val person1ModelFqn = ModelFqn("people", "person1")
  val person2ModelFqn = ModelFqn("people", "person2")
  val nonExistingModelFqn = ModelFqn("people", "noPerson")
  
  "A ModelSnapshotStore" when {

    "when creating a snapshot" must {
      "be able to get the snapshot that was created" in withModelSnapshotStore { store =>
        val version = 2L
        val timestamp = 4L

        val created = SnapshotData(
          SnapshotMetaData(person1ModelFqn, version, timestamp),
          JObject("key" -> JNull))
          
        store.createSnapshot(created)

        val queried = store.getSnapshot(person1ModelFqn, version)
        assert(queried.get == created)
      }
    }
    
    "when getting a specific snapshot" must {
      "return the correct snapshot when one exists" in withModelSnapshotStore { store =>
        val queried = store.getSnapshot(person1ModelFqn, 0L)
        assert(queried.isDefined)
      }
      
      "return None when the specified version does not exist" in withModelSnapshotStore { store =>
        val queried = store.getSnapshot(person1ModelFqn, 5L)
        assert(queried.isEmpty)
      }
      
      "return None when the specified model does not exist" in withModelSnapshotStore { store =>
        val queried = store.getSnapshot(nonExistingModelFqn, 0L)
        assert(queried.isEmpty)
      }
    }
    
    "when getting the latest snapshot for a model" must {
      "return the correct meta data for a model with snapshots" in withModelSnapshotStore { store =>
        val metaData = store.getLatestSnapshotMetaDataForModel(person1ModelFqn)
        assert(metaData.isDefined)
        assert(metaData.get.version == 20L)
      }
      
      "return None when the specified model does not exist" in withModelSnapshotStore { store =>
        val queried = store.getSnapshot(nonExistingModelFqn, 0L)
        assert(queried.isEmpty)
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
      dbPool.close()
      db.activateOnCurrentThread()
      db.drop() // Drop will close and drop
    }
  }

  object CommandListener extends OCommandOutputListener() {
    def onMessage(iText: String) = {
    }
  }
}