package com.convergencelabs.server.datastore.domain

import org.scalatest.WordSpec
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.BeforeAndAfterAll
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.common.log.OLogManager

class ModelStoreSpec extends WordSpec {

  OLogManager.instance().setConsoleLevel("WARNING")

  def initDB(uri: String): ODatabaseDocumentTx = {
    val db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()
    db.create()
    val listener = new OCommandOutputListener() {
      def onMessage(iText: String) = {
      }
    }
    val file = getClass.getResource("/dbfiles/domain-test-db.gz").getFile()
    val dbImport = new ODatabaseImport(db, file, listener)
    dbImport.importDatabase()
    dbImport.close()
    db
  }

  "An OrientDBModelStore" when {
    
    "asked whether a model exists" must {
      
      "return false if it doesn't exist" in {
        val db = initDB("memory:ms1")
        val dbPool = new OPartitionedDatabasePool("memory:ms1", "admin", "admin")
        val modelStore = new ModelStore(dbPool)
        assert(!modelStore.modelExists(ModelFqn("notReal", "notReal")))
        db.activateOnCurrentThread()
        db.close()
      }

      "return true if it does exist" in {
        val db = initDB("memory:ms2")
        val dbPool = new OPartitionedDatabasePool("memory:ms2", "admin", "admin")
        val modelStore = new ModelStore(dbPool)
        assert(modelStore.modelExists(ModelFqn("tests", "test")))
        db.activateOnCurrentThread()
        db.close()
      }
    }
    "retrieving model data" must {
      "return None if it doesn't exist" in {
        val db = initDB("memory:ms3")
        val dbPool = new OPartitionedDatabasePool("memory:ms3", "admin", "admin")
        val modelStore = new ModelStore(dbPool)
        assert(modelStore.getModelData(ModelFqn("notReal", "notReal")).isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }

      "return Some if it does exist" in {
        val db = initDB("memory:ms4")
        val dbPool = new OPartitionedDatabasePool("memory:ms4", "admin", "admin")
        val modelStore = new ModelStore(dbPool)
        assert(!modelStore.getModelData(ModelFqn("tests", "test")).isEmpty)
        db.activateOnCurrentThread()
        db.close()
      }
    }
  }
}
