package com.convergencelabs.server.datastore.domain

import org.scalatest.WordSpec
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.BeforeAndAfterAll
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener

class ModelStoreSpec extends WordSpec with BeforeAndAfterAll {

  override def beforeAll() {

  }

  val defaultCommandListener = new OCommandOutputListener() {
    def onMessage(iText: String) = {}
  }

  "An OrientDBModelStore" when {
    "asked whether a model exists" must {
      "return false if it doesn't exist" in {
        val db = new ODatabaseDocumentTx("memory:test1")
        db.activateOnCurrentThread()
        db.create()
        val file = getClass.getResource("/dbfiles/domain-test-db.gz").getFile();
        val dbImport = new ODatabaseImport(db, file, defaultCommandListener)
        dbImport.importDatabase()
        dbImport.close()

        val dbPool = new OPartitionedDatabasePool("memory:test1", "admin", "admin")
        val modelStore = new ModelStore(dbPool)
        assert(!modelStore.modelExists(ModelFqn("notReal", "notReal")))
      }

      "return true if it does exist" in {
        val db = new ODatabaseDocumentTx("memory:test2")
        db.activateOnCurrentThread()
        db.create()
        val file = getClass.getResource("/dbfiles/domain-test-db.gz").getFile();
        val dbImport = new ODatabaseImport(db, file, defaultCommandListener)
        dbImport.importDatabase()
        dbImport.close()

        val dbPool = new OPartitionedDatabasePool("memory:test2", "admin", "admin")
        val modelStore = new ModelStore(dbPool)
        assert(modelStore.modelExists(ModelFqn("tests", "test")))
      }

    }
  }
}
