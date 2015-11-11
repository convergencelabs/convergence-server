package com.convergencelabs.server.datastore.domain

import org.scalatest.WordSpec
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.BeforeAndAfterAll
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.common.log.OLogManager
import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ot.ops.StringRemoveOperation
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JInt
import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import org.json4s.JsonAST.JArray
import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import org.json4s.JsonAST.JArray
import com.convergencelabs.server.domain.model.ot.ops.ArrayMoveOperation
import org.scalatest.OptionValues

class ModelStoreSpec extends WordSpec with PersistenceStoreSpec[ModelStore] with OptionValues {

  def createStore(dbPool: OPartitionedDatabasePool): ModelStore = new ModelStore(dbPool)

  "An ModelStore" when {

    "asked whether a model exists" must {

      "return false if it doesn't exist" in withPersistenceStore { store =>
        assert(!store.modelExists(ModelFqn("notReal", "notReal")))
      }

      "return true if it does exist" in withPersistenceStore { store =>
        assert(store.modelExists(ModelFqn("people", "person1")))
      }
    }
    "retrieving model data" must {
      "return None if it doesn't exist" in withPersistenceStore { store =>
        assert(store.getModelData(ModelFqn("notReal", "notReal")).isEmpty)
      }

      "return Some if it does exist" in withPersistenceStore { store =>
        assert(!store.getModelData(ModelFqn("people", "person1")).isEmpty)
      }
    }
  }
}
