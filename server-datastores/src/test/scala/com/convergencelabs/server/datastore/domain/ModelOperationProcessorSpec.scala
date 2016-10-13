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
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation

// scalastyle:off magic.number multiple.string.literals
class ModelOperationProcessorSpec
    extends PersistenceStoreSpec[(ModelOperationProcessor, ModelOperationStore, ModelStore)]("/dbfiles/domain-n1-d1.json.gz")
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
  val lnameField = "lname"
  val emailsField = "emails"
  val ageField = "age"
  val marriedField = "married"

  val modelFqn = ModelFqn("people", "person1")
  val notFoundFqn = ModelFqn("Does Not", "Exist")

  def createStore(dbPool: OPartitionedDatabasePool): (ModelOperationProcessor, ModelOperationStore, ModelStore) =
    (new ModelOperationProcessor(dbPool),
      new ModelOperationStore(dbPool),
      new ModelStore(dbPool, new ModelOperationStore(dbPool), new ModelSnapshotStore(dbPool)))

  "A ModelOperationProcessor" when {

    "applying a noOp'ed discrete operation" must {
      "not apply the operation" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedStringInsertOperation(fnameVID, true, 0, "abc")
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "john")
      }
    }

    "applying a compound operation" must {
      "apply all operations in the compound operation" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op1 = AppliedStringInsertOperation(fnameVID, false, 0, "x")
        val op2 = AppliedStringInsertOperation(fnameVID, false, 1, "y")

        val compound = AppliedCompoundOperation(List(op1, op2))

        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, compound)
        val response = processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "xyjohn")
      }

      "apply all operations in rename compound operation" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op1 = AppliedObjectRemovePropertyOperation("pp1-data", false, lnameField, Some(StringValue("oldId", "oldValue")))
        val op2 = AppliedObjectAddPropertyOperation("pp1-data", false, "newName", StringValue("idididi", "somethingelse"))

        val compound = AppliedCompoundOperation(List(op1, op2))

        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, compound)
        val response = processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children("newName") shouldEqual StringValue("idididi", "somethingelse")
      }

      "not apply noOp'ed operations in the compound operation" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op1 = AppliedStringInsertOperation(fnameVID, false, 0, "x")
        val op2 = AppliedStringInsertOperation(fnameVID, true, 1, "y")

        val compound = AppliedCompoundOperation(List(op1, op2))

        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, compound)
        val response = processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "xjohn")
      }
    }

    "applying string operations" must {
      "correctly update the model on StringInsert" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedStringInsertOperation(fnameVID, false, 0, "abc")
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "abcjohn")
      }

      "correctly update the model on StringRemove" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedStringRemoveOperation(fnameVID, false, 1, 2, Some("Oh"))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "jn")
      }

      "correctly update the model on StringSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedStringSetOperation(fnameVID, false, "new string", Some("oldValue"))
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
        val op = AppliedArrayInsertOperation(emailsVID, false, 0, insertVal)
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
        val (processor, opStore, modelStore) = stores

        val op = AppliedArrayRemoveOperation(emailsVID, false, 0, Some(StringValue("oldId", "removedValue")))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(emailsField) match {
          case ArrayValue(vid, children) => {
            children.size shouldBe 2
          }
          case _ => fail
        }
      }

      "correctly update the model on ArrayReplace" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val replaceVal = ObjectValue("art-data", Map("field1" -> StringValue("art-f1", "someValue"), "field2" -> DoubleValue("art-f2", 5)))
        val op = AppliedArrayReplaceOperation(emailsVID, false, 0, replaceVal, Some(StringValue("oldId", "removedValue")))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(emailsField) match {
          case ArrayValue(vid, children) => {
            children(0) shouldBe replaceVal
            children.size shouldBe 3
          }
          case _ => fail
        }
      }

      "correctly update the model on ArrayMove" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedArrayMoveOperation(emailsVID, false, 0, 2)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(emailsField) shouldBe ArrayValue(emailsVID, List(
          StringValue("pp1-email2", "second@email.com"),
          StringValue("pp1-email3", "another@email.com"),
          StringValue("pp1-email1", "first@email.com")))
      }

      "correctly update the model on ArraySet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val setValue = List(StringValue("as-sv", "someValue"), StringValue("as-sov", "someOtherValue"))
        val op = AppliedArraySetOperation(emailsVID, false, setValue, Some(List[DataValue]()))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(emailsField) match {
          case ArrayValue(vid, children) => {
            children shouldEqual setValue
          }
          case _ => fail
        }
      }
    }

    "applying object operations" must {
      "correctly update the model on ObjectAddProperty" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedObjectAddPropertyOperation("pp1-data", false, "addedProperty", StringValue("aoo-value", "value"))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children("addedProperty") shouldEqual StringValue("aoo-value", "value")
      }
      
      "correctly update the model on ObjectAddProperty with a special char" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedObjectAddPropertyOperation("pp1-data", false, "prop-with-dash", StringValue("aoo-value", "value"))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children("prop-with-dash") shouldEqual StringValue("aoo-value", "value")
      }

      "correctly update the model on ObjectSetProperty" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedObjectSetPropertyOperation("pp1-data", false, fnameField, StringValue("pp1-fnbob", "bob"), Some(StringValue("oldId", "oldVal")))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(fnameField) shouldBe StringValue("pp1-fnbob", "bob")
      }

      "correctly update the model on ObjectRemoveProperty" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedObjectRemovePropertyOperation("pp1-data", false, fnameField, Some(StringValue("oldId", "oldVal")))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children.get(fnameField) shouldBe None
      }
      
      "correctly update the model on ObjectRemoveProperty with dash" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores
        
        val propWithDash = "prop-with-dash"

        val addOp = AppliedObjectAddPropertyOperation("pp1-data", false, propWithDash, StringValue("aoo-value", "value"))
        processor.processModelOperation(ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, addOp)).success
        
        val op = AppliedObjectRemovePropertyOperation("pp1-data", false, propWithDash, Some(StringValue("aoo-value", "value")))
        val modelOp = ModelOperation(modelFqn, startingVersion  + 1, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children.get(propWithDash) shouldBe None
      }

      "correctly update the model on ObjectSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val replacePerson = Map("fname" -> StringValue("pp1-fnbob", "bob"), "lname" -> StringValue("pp1-lnsmith", "smith"))
        val op = AppliedObjectSetOperation("pp1-data", false, replacePerson, Some(Map("fname" -> StringValue("oldId1", "yo"), "lname" -> StringValue("oldId2", "yoyo"))))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children shouldBe replacePerson
      }
    }

    "applying number operations" must {
      "correctly update the model on NumberAdd" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedNumberAddOperation(ageVID, false, 5)
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp)

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(ageField) shouldBe DoubleValue(ageVID, 31)
      }

      "correctly update the model on NumberSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedNumberSetOperation(ageVID, false, 33, Some(22))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp)

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(ageField) shouldBe DoubleValue(ageVID, 33)
      }
    }

    "applying boolean operations" must {
      "correctly update the model on BooleanSet" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val op = AppliedBooleanSetOperation(marriedVID, false, true, Some(false))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        val response = processor.processModelOperation(modelOp)

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(marriedField) shouldBe BooleanValue(marriedVID, true)
      }
    }

    "handling non specialy object preoprty names" must {

      "correctly add property names that start with a period" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val property = "my-prop!"
        val addOp = AppliedObjectAddPropertyOperation("pp1-data", false, ".value", StringValue("aoo-value", "value"))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, addOp)

        processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(".value") shouldBe StringValue("aoo-value", "value")
      }
      
      "correctly update property names that start with a period" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val property = "my-prop!"
        val addOp = AppliedObjectAddPropertyOperation("pp1-data", false, ".value", StringValue("aoo-value", "initial"))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, addOp)

        processor.processModelOperation(modelOp).success
        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(".value") shouldBe StringValue("aoo-value", "initial")
        
        val setOp = AppliedObjectSetPropertyOperation("pp1-data", false, ".value", StringValue("aoo-value1", "updated"), Some(StringValue("aoo-value", "initial")))
        val setModelOp = ModelOperation(modelFqn, startingVersion + 1, Instant.now(), uid, sid, setOp)

        processor.processModelOperation(setModelOp).success
        val updatedModelData = modelStore.getModelData(modelFqn).success.value.value
        updatedModelData.children(".value") shouldBe StringValue("aoo-value1", "updated")
      }

      "correctly handle property names in the path that are numeric" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val property = "4"
        val op = AppliedObjectAddPropertyOperation("pp1-data", false, property, StringValue("aoo-value", "value"))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(property) shouldBe StringValue("aoo-value", "value")
      }
      
      "correctly handle property names in the path that have a dash" in withPersistenceStore { stores =>
        val (processor, opStore, modelStore) = stores

        val property = "a-dash"
        val op = AppliedObjectAddPropertyOperation("pp1-data", false, property, StringValue("aoo-value", "value"))
        val modelOp = ModelOperation(modelFqn, startingVersion, Instant.now(), uid, sid, op)
        processor.processModelOperation(modelOp).success

        val modelData = modelStore.getModelData(modelFqn).success.value.value
        modelData.children(property) shouldBe StringValue("aoo-value", "value")
      }
    }
  }
}
