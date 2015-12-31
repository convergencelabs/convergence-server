package com.convergencelabs.server.datastore.domain

import java.time.Instant
import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JBool
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JNothing
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
import org.json4s.jvalue2monadic
import org.scalatest.Matchers
import org.scalatest.OptionValues
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.convergencelabs.server.domain.model.ot.CompoundOperation

class ModelOperationProcessorSpec
    extends PersistenceStoreSpec[(ModelOperationProcessor, ModelOperationStore, ModelStore)]("/dbfiles/domain.json.gz")
    with WordSpecLike
    with OptionValues
    with Matchers {

  val startingVersion = 100
  val uid = "u1"
  val sid = "u1-1"

  val fnameField = "fname"
  val emailsField = "emails"
  val ageField = "age"
  val marriedField = "married"

  val modelFqn = ModelFqn("people", "person1")
  val notFoundFqn = ModelFqn("Does Not", "Exist")

  def createStore(dbPool: OPartitionedDatabasePool): (ModelOperationProcessor, ModelOperationStore, ModelStore) =
    (new ModelOperationProcessor(dbPool),
      new ModelOperationStore(dbPool),
      new ModelStore(dbPool))

  "A ModelOperationProcessor" when {
    
    "applying a noOp'ed discrete operation" must {
      "not apply the operation" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = StringInsertOperation(List(fnameField), true, 0, "abc")
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ fnameField shouldBe JString("john")
      }
    }
    
    "applying a compound operation" must {
      "apply all operations in the compound operation" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op1 = StringInsertOperation(List(fnameField), false, 0, "x")
        val op2 = StringInsertOperation(List(fnameField), false, 1, "y")
        
        val compound = CompoundOperation(List(op1, op2))
        
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, compound)
        val response = processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ fnameField shouldBe JString("xyjohn")
      }
      
      "not apply noOp'ed operations in the compound operation" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op1 = StringInsertOperation(List(fnameField), false, 0, "x")
        val op2 = StringInsertOperation(List(fnameField), true, 1, "y")
        
        val compound = CompoundOperation(List(op1, op2))
        
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, compound)
        val response = processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ fnameField shouldBe JString("xjohn")
      }
    }
    
    "applying string operations" must {
      "correctly update the model on StringInsert" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = StringInsertOperation(List(fnameField), false, 0, "abc")
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ fnameField shouldBe JString("abcjohn")
      }

      "correctly update the model on StringRemove" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = StringRemoveOperation(List(fnameField), false, 1, "oh")
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ fnameField shouldBe JString("jn")
      }

      "correctly update the model on StringSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = StringSetOperation(List(fnameField), false, "new string")
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ fnameField shouldBe JString("new string")
      }
    }

    "applying array operations" must {
      "correctly update the model on ArrayInsert" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val insertVal = JObject("field1" -> JString("someValue"), "field2" -> JInt(5))
        val op = ArrayInsertOperation(List(emailsField), false, 0, insertVal)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        (modelData \ emailsField) match {
          case JArray(array) => {
            array(0) shouldBe insertVal
          }
          case _ => fail
        }
      }

      "correctly update the model on ArrayRemove" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = ArrayRemoveOperation(List(emailsField), false, 0)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        (modelData \ emailsField) match {
          case JArray(array) => {
            array.size shouldBe 2
          }
          case _ => fail
        }
      }

      "correctly update the model on ArrayReplace" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val replaceVal = JObject("field1" -> JString("someValue"), "field2" -> JInt(5))
        val op = ArrayReplaceOperation(List(emailsField), false, 0, replaceVal)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        (modelData \ emailsField) match {
          case JArray(array) => {
            array(0) shouldBe replaceVal
            array.size shouldBe 3
          }
          case _ => fail
        }
      }

      "correctly update the model on ArrayMove" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = ArrayMoveOperation(List(emailsField), false, 0, 2)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        (modelData \ emailsField) shouldBe JArray(List(
            JString("second@email.com"),
            JString("another@email.com"),
            JString("first@email.com")
            ))
      }

      "correctly update the model on ArraySet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val setValue = JArray(List(JString("someValue"), JString("someOtherVale")))
        val op = ArraySetOperation(List(emailsField), false, setValue)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        (modelData \ emailsField) shouldBe setValue
      }
    }

    "applying object operations" must {
      "correctly update the model on ObjectAddProperty" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = ObjectAddPropertyOperation(List(), false, "addedProperty", new JString("value"))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ "addedProperty" shouldBe JString("value")
      }

      "correctly update the model on ObjectSetProperty" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = ObjectSetPropertyOperation(List(), false, fnameField, new JString("bob"))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ fnameField shouldBe JString("bob")
      }

      "correctly update the model on ObjectRemoveProperty" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = ObjectRemovePropertyOperation(List(), false, fnameField)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ fnameField shouldBe JNothing
      }

      "correctly update the model on ObjectSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val replacePerson = JObject("fname" -> JString("bob"), "lname" -> JString("smith"))
        val op = ObjectSetOperation(List(), false, replacePerson)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData shouldBe replacePerson
      }
    }

    "applying number operations" must {
      "correctly update the model on NumberAdd" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = NumberAddOperation(List(ageField), false, JInt(5))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp)

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ ageField shouldBe JInt(31)
      }

      "correctly update the model on NumberSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = NumberSetOperation(List(ageField), false, JInt(33))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp)

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ ageField shouldBe JInt(33)
      }
    }

    "applying boolean operations" must {
      "correctly update the model on BooleanSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = BooleanSetOperation(List(marriedField), false, true)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp)

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData \ marriedField shouldBe JBool(true)
      }
    }
  }
}
