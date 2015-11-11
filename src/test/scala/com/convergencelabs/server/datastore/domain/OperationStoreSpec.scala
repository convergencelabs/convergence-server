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

class OperationStoreSpec extends WordSpec with PersistenceStoreSpec[OperationStore] with OptionValues {

  def createStore(dbPool: OPartitionedDatabasePool): OperationStore = new OperationStore(dbPool)

  "An OperationStore" when {

//    "applying operations" must {
//      "correctly update the model on StringInsert" in withPersistenceStore { store =>
//        store.processOperation(ModelFqn("people", "person1"), StringInsertOperation(List("fname"), false, 0, "abc"), 0, 0, "me")
//        val modelData = store.getModelData(ModelFqn("people", "person1")).value
//        assert(modelData.data \ "fname" == JString("abcjohn"))
//      }
//
//      "correctly update the model on StringRemove" in withPersistenceStore { store =>
//        store.applyOperationToModel(ModelFqn("people", "person1"), StringRemoveOperation(List("fname"), false, 1, "oh"), 0, 0, "me")
//        val modelData = store.getModelData(ModelFqn("people", "person1")).value
//        assert(modelData.data \ "fname" == JString("jn"))
//      }
//
//      "correctly update the model on ArrayInsert" in withPersistenceStore { store =>
//        val insertVal = JObject("field1" -> JString("someValue"), "field2" -> JInt(5))
//        store.applyOperationToModel(ModelFqn("people", "person1"), ArrayInsertOperation(List("emails"), false, 0, insertVal), 0, 0, "me")
//        val modelData = store.getModelData(ModelFqn("people", "person1")).value
//        assert((modelData.data \ "emails")(0) == insertVal)
//      }
//
//      "correctly update the model on ArrayRemove" in withPersistenceStore { store =>
//        store.applyOperationToModel(ModelFqn("people", "person1"), ArrayRemoveOperation(List("emails"), false, 0), 0, 0, "me")
//        val modelData = store.getModelData(ModelFqn("people", "person1")).value
//        assert((modelData.data \ "emails").asInstanceOf[JArray].arr.size == 2)
//      }
//
//      "correctly update the model on ArrayReplace" in withPersistenceStore { store =>
//        val replaceVal = JObject("field1" -> JString("someValue"), "field2" -> JInt(5))
//        store.applyOperationToModel(ModelFqn("people", "person1"), ArrayReplaceOperation(List("emails"), false, 0, replaceVal), 0, 0, "me")
//        val modelData = store.getModelData(ModelFqn("people", "person1")).value
//        
//        (modelData.data \ "emails") match {
//          case JArray(array) => {
//            assert(array(0) == replaceVal)
//            assert(array.size == 3)
//          }
//          case _ => fail
//        }
//      }
//
//      "correctly update the model on ArrayMove" in withPersistenceStore { store =>
//        store.applyOperationToModel(ModelFqn("people", "person1"), ArrayMoveOperation(List("emails"), false, 0, 2), 0, 0, "me")
//        val modelData = store.getModelData(ModelFqn("people", "person1")).value
//      }
//    }
  }
}
