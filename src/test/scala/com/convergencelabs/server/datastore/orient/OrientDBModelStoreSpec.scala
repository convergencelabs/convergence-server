package com.convergencelabs.server.datastore.orient

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import org.scalatest.BeforeAndAfter
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.BeforeAndAfterAll

class OrientDBModelStoreSpec extends WordSpec with BeforeAndAfterAll {

  override def beforeAll() {
    val db = new ODatabaseDocumentTx("memory:modeltest")
    db.create()
    db.getMetadata().getSchema().createClass("model")
  }
   
  
  "An OrientDBModelStore" when {
    "asked whether a model exists" must {
      "return false if it doesn't exist" in {
        val dbPool = new OPartitionedDatabasePool("memory:modeltest", "admin", "admin")
        val modelStore = new OrientDBModelStore(dbPool)
        assert(!modelStore.modelExists(ModelFqn("notReal", "notReal")))
      }
      
      "return true if it does exist" in {
        val dbPool = new OPartitionedDatabasePool("memory:modeltest", "admin", "admin")
        val modelStore = new OrientDBModelStore(dbPool)
        assert(!modelStore.modelExists(ModelFqn("notReal", "notReal")))
      }
      
    }
  }
}

