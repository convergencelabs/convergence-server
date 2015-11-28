package com.convergencelabs.server.datastore.domain

import org.scalatest.WordSpecLike
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ModelFqn
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import org.scalatest.BeforeAndAfterAll
import com.orientechnologies.orient.core.db.tool.ODatabaseImport
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.common.log.OLogManager
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import org.json4s.JsonAST.JString
import org.json4s.JsonAST.JValue
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JInt
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import org.json4s.JsonAST.JArray
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import org.json4s.JsonAST.JArray
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import org.scalatest.OptionValues._
import org.scalatest.TryValues._
import org.scalatest.Matchers

class ModelStoreSpec
    extends PersistenceStoreSpec[ModelStore]("/dbfiles/domain.json.gz")
    with WordSpecLike
    with Matchers {

  def createStore(dbPool: OPartitionedDatabasePool): ModelStore = new ModelStore(dbPool)

  "An ModelStore" when {

    "asked whether a model exists" must {

      "return false if it doesn't exist" in withPersistenceStore { store =>
        store.modelExists(ModelFqn("notReal", "notReal")).success.value shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { store =>
        store.modelExists(ModelFqn("people", "person1")).success.value shouldBe true
      }
    }
    "retrieving model data" must {
      "return None if it doesn't exist" in withPersistenceStore { store =>
        store.getModelData(ModelFqn("notReal", "notReal")).success.value shouldBe None
      }

      "return Some if it does exist" in withPersistenceStore { store =>
        store.getModelData(ModelFqn("people", "person1")).success.value shouldBe defined
      }
    }
  }
}
