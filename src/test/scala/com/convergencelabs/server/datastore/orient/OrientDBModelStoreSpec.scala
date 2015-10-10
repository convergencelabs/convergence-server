package com.convergencelabs.server.datastore.orient

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import org.scalatest.BeforeAndAfter
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.BeforeAndAfterAll
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import java.io.FileReader
import java.io.BufferedInputStream
import java.io.FileInputStream

class OrientDBModelStoreSpec extends WordSpec with BeforeAndAfterAll {

  override def beforeAll() {
    val db = new ODatabaseDocumentTx("memory:test2")
    db.create()
    val listener = new OCommandOutputListener() {
      def onMessage(iText: String) = {
        println(iText)
      }
    }
    val file = getClass.getResource("/dbfiles/domain-test-db.gz").getFile();
    val dbImport = new ODatabaseImport(db, file, listener)
    dbImport.importDatabase()
    dbImport.close()
    db.close()
  }
   
  
  "An OrientDBModelStore" when {
    "asked whether a model exists" must {
      "return false if it doesn't exist" in {
        val dbPool = new OPartitionedDatabasePool("memory:test2", "admin", "admin")
        val modelStore = new OrientDBModelStore(dbPool)
        assert(!modelStore.modelExists(ModelFqn("notReal", "notReal")))
      }
      
      "return true if it does exist" in {
        val dbPool = new OPartitionedDatabasePool("memory:test2", "admin", "admin")
        val modelStore = new OrientDBModelStore(dbPool)
        assert(modelStore.modelExists(ModelFqn("tests", "test")))
      }
      
    }
  }
}

