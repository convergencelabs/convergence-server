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
import org.scalatest.Finders
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
import org.json4s.JsonAST.JDouble
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.BooleanValue

// scalastyle:off magic.number multiple.string.literals
class ModelOperationProcessorSpec
    extends PersistenceStoreSpec[(ModelOperationProcessor, ModelOperationStore, ModelStore)]("/dbfiles/domain.json.gz")
    with WordSpecLike
    with OptionValues
    with Matchers {

  val startingVersion = 100
  val uid = "u1"
  val sid = "u1-1"

  val fnameVID = "pp1-fname"
  val emailsVID = "pp1-emails"
  val ageVID = "pp1-age"
  val marriedVID = "pp1-married"
  
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

        val op = StringInsertOperation(fnameVID, true, 0, "abc")
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "john")
      }
    }

    "applying a compound operation" must {
      "apply all operations in the compound operation" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op1 = StringInsertOperation(fnameVID, false, 0, "x")
        val op2 = StringInsertOperation(fnameVID, false, 1, "y")

        val compound = CompoundOperation(List(op1, op2))

        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, compound)
        val response = processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "xyjohn")
      }

      "not apply noOp'ed operations in the compound operation" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op1 = StringInsertOperation(fnameVID, false, 0, "x")
        val op2 = StringInsertOperation(fnameVID, true, 1, "y")

        val compound = CompoundOperation(List(op1, op2))

        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, compound)
        val response = processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "xjohn")
      }
    }

    "applying string operations" must {
      "correctly update the model on StringInsert" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = StringInsertOperation(fnameVID, false, 0, "abc")
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "abcjohn")
      }

      "correctly update the model on StringRemove" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = StringRemoveOperation(fnameVID, false, 1, "oh")
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "jn")
      }

      "correctly update the model on StringSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = StringSetOperation(fnameVID, false, "new string")
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "new string")
      }
    }

    "applying array operations" must {
      "correctly update the model on ArrayInsert" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val insertVal = ObjectValue("pp1-f1", Map("field1" -> StringValue("pp1-sv", "someValue"), "field2" -> DoubleValue("pp1-5", 5)))
        val op = ArrayInsertOperation(emailsVID, false, 0, insertVal)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(emailsField) match {
          case ArrayValue(vid, children) => {
            children(0) shouldBe insertVal
          }
          case _ => fail
        }
      }

      "correctly update the model on ArrayRemove" in withPersistenceStore { stores =>
//        val (processor, opStore, modelStore) = stores
//
//        val op = ArrayRemoveOperation(List(emailsField), false, 0)
//        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
//        processor.processModelOperation(modelOp).success
//
//        val modelData = modelStore.getModelData(modelFqn).success.value.value
//        (modelData \ emailsField) match {
//          case JArray(array) => {
//            array.size shouldBe 2
//          }
//          case _ => fail
//        }
      }

      "correctly update the model on ArrayReplace" in withPersistenceStore { stores =>
//        val (processor, opStore, modelStore) = stores
//
//        val replaceVal = JObject("field1" -> JString("someValue"), "field2" -> JInt(5))
//        val op = ArrayReplaceOperation(List(emailsField), false, 0, replaceVal)
//        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
//        processor.processModelOperation(modelOp).success
//
//        val modelData = modelStore.getModelData(modelFqn).success.value.value
//        (modelData \ emailsField) match {
//          case JArray(array) => {
//            array(0) shouldBe replaceVal
//            array.size shouldBe 3
//          }
//          case _ => fail
//        }
      }

      "correctly update the model on ArrayMove" in withPersistenceStore { stores =>
//        val (processor, opStore, modelStore) = stores
//
//        val op = ArrayMoveOperation(List(emailsField), false, 0, 2)
//        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
//        processor.processModelOperation(modelOp).success
//
//        val modelData = modelStore.getModelData(modelFqn).success.value.value
//        (modelData \ emailsField) shouldBe JArray(List(
//          JString("second@email.com"),
//          JString("another@email.com"),
//          JString("first@email.com")))
      }

      "correctly update the model on ArraySet" in withPersistenceStore { stores =>
//        val (processor, opStore, modelStore) = stores
//
//        val setValue = JArray(List(JString("someValue"), JString("someOtherVale")))
//        val op = ArraySetOperation(List(emailsField), false, setValue)
//        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
//        processor.processModelOperation(modelOp).success
//
//        val modelData = modelStore.getModelData(modelFqn).success.value.value
//        (modelData \ emailsField) shouldBe setValue
      }
    }

    "applying object operations" must {
      "correctly update the model on ObjectAddProperty" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = ObjectAddPropertyOperation("pp1-data", false, "addedProperty", StringValue("aoo-value", "value"))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children("addedProperty") shouldEqual StringValue("aoo-value", "value")
      }

      "correctly update the model on ObjectSetProperty" in withPersistenceStore { stores =>
//        val (processor, opStore, modelStore) = stores
//
//        val op = ObjectSetPropertyOperation(List(), false, fnameField, new JString("bob"))
//        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
//        processor.processModelOperation(modelOp).success
//
//        val modelData = modelStore.getModelData(modelFqn).success.value.value
//        modelData \ fnameField shouldBe JString("bob")
      }

      "correctly update the model on ObjectRemoveProperty" in withPersistenceStore { stores =>
//        val (processor, opStore, modelStore) = stores
//
//        val op = ObjectRemovePropertyOperation(List(), false, fnameField)
//        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
//        processor.processModelOperation(modelOp).success
//
//        val modelData = modelStore.getModelData(modelFqn).success.value.value
//        modelData \ fnameField shouldBe JNothing
      }

      "correctly update the model on ObjectSet" in withPersistenceStore { stores =>
//        val (processor, opStore, modelStore) = stores
//
//        val replacePerson = JObject("fname" -> JString("bob"), "lname" -> JString("smith"))
//        val op = ObjectSetOperation(List(), false, replacePerson)
//        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
//        processor.processModelOperation(modelOp).success
//
//        val modelData = modelStore.getModelData(modelFqn).success.value.value
//        modelData shouldBe replacePerson
      }
    }

    "applying number operations" must {
      "correctly update the model on NumberAdd" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = NumberAddOperation(ageVID, false, 5)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp)

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(ageField) shouldBe DoubleValue(ageVID, 31)
      }

      "correctly update the model on NumberSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = NumberSetOperation(ageVID, false, 33)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp)

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(ageField) shouldBe DoubleValue(ageVID, 33)
      }
    }

    "applying boolean operations" must {
      "correctly update the model on BooleanSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = BooleanSetOperation(marriedVID, false, true)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp)

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(marriedField) shouldBe BooleanValue(marriedVID, true)
      }
    }

    "handling non alpha object preoprties" must {

      "correctly handle property names in the path with special chars" in withPersistenceStore { stores =>
//        val (processor, opStore, modelStore) = stores
//
//        val property = "my-prop!"
//        val op = ObjectAddPropertyOperation(List(), false, property, new JString("value"))
//        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
//        processor.processModelOperation(modelOp).success
//
//        val modelData = modelStore.getModelData(modelFqn).success.value.value
//        modelData \ property shouldBe JString("value")
      }

      "correctly handle property names in the path that are numeric" in withPersistenceStore { stores =>
//        val (processor, opStore, modelStore) = stores
//
//        val property = "4"
//        val op = ObjectAddPropertyOperation(List(), false, property, new JString("value"))
//        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
//        processor.processModelOperation(modelOp).success
//
//        val modelData = modelStore.getModelData(modelFqn).success.value.value
//        modelData \ property shouldBe JString("value")
      }
    }
  }
}
