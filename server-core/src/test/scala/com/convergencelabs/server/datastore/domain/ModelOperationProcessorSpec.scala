package com.convergencelabs.server.datastore.domain

import java.time.Duration
import java.time.Instant

import scala.language.postfixOps
import scala.util.Try

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.OptionValues
import org.scalatest.WordSpecLike

import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.NewModelOperation
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DateValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import com.convergencelabs.server.datastore.OrientDBUtil
import scala.util.Success

// scalastyle:off magic.number multiple.string.literals
class ModelOperationProcessorSpec
  extends PersistenceStoreSpec[DomainPersistenceProvider](DeltaCategory.Domain)
  with WordSpecLike
  with OptionValues
  with Matchers {

  val username = "test"
  val user = DomainUser(DomainUserType.Normal, username, None, None, None, None)

  val sid = "u1-1"
  val session = DomainSession(sid, username, Instant.now(), None, "jwt", "js", "1.0", "", "127.0.0.1")

  val modelPermissions = ModelPermissions(true, true, true, true)

  val startingVersion = 100

  val peopleCollectionId = "people"
  val person1Id = "person1"
  val person1MetaData = ModelMetaData(
    peopleCollectionId,
    person1Id,
    startingVersion,
    Instant.now(),
    Instant.now(),
    true,
    modelPermissions,
    1)

  val person1VID = "pp1-data"
  val fnameVID = "pp1-fname"
  val lnameVID = "pp1-lname"
  val emailsVID = "pp1-emails"
  val ageVID = "pp1-age"
  val bornVID = "pp1-born"
  val marriedVID = "pp1-married"
  val email1VID = "pp1-email1"
  val email2VID = "pp1-email2"
  val email3VID = "pp1-email3"
  val spouseVID = "pp1-spouse"

  val fnameField = "fname"
  val lnameField = "lname"
  val emailsField = "emails"
  val ageField = "age"
  val spouseField = "spouse"
  val marriedField = "married"
  val bornField = "born"

  val bornDate = Instant.now().minus(Duration.ofDays(6000))

  val person1Data = ObjectValue(person1VID, Map(
    fnameField -> StringValue(fnameVID, "john"),
    lnameField -> StringValue(lnameVID, "smith"),
    ageField -> DoubleValue(ageVID, 26),
    bornField -> DateValue(bornVID, bornDate),
    marriedField -> BooleanValue(marriedVID, false),
    spouseField -> NullValue(spouseVID),
    emailsField -> ArrayValue(emailsVID, List(
      StringValue(email1VID, "first@email.com"),
      StringValue(email2VID, "second@email.com"),
      StringValue(email3VID, "another@email.com")))))
  val person1Model = Model(person1MetaData, person1Data)

  def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider = new DomainPersistenceProviderImpl(dbProvider)

  "A ModelOperationProcessor" when {

    "applying a noOp'ed discrete operation" must {
      "not apply the operation" in withTestData { provider =>
        val op = AppliedStringInsertOperation(fnameVID, true, 0, "abc")
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "john")
      }
    }

    "applying a compound operation" must {
      "apply all operations in the compound operation" in withTestData { provider =>
        val op1 = AppliedStringInsertOperation(fnameVID, false, 0, "x")
        val op2 = AppliedStringInsertOperation(fnameVID, false, 1, "y")

        val compound = AppliedCompoundOperation(List(op1, op2))

        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, compound)
        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "xyjohn")
      }

      "apply all operations in rename compound operation" in withTestData { provider =>
        val op1 = AppliedObjectRemovePropertyOperation(person1VID, false, lnameField, Some(StringValue("oldId", "oldValue")))
        val op2 = AppliedObjectAddPropertyOperation(person1VID, false, "newName", StringValue("idididi", "somethingelse"))

        val compound = AppliedCompoundOperation(List(op1, op2))

        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, compound)
        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children("newName") shouldEqual StringValue("idididi", "somethingelse")
      }

      "not apply noOp'ed operations in the compound operation" in withTestData { provider =>
        val op1 = AppliedStringInsertOperation(fnameVID, false, 0, "x")
        val op2 = AppliedStringInsertOperation(fnameVID, true, 1, "y")

        val compound = AppliedCompoundOperation(List(op1, op2))

        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, compound)
        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "xjohn")
      }
    }

    "applying string operations" must {
      "correctly update the model on StringInsert" in withTestData { provider =>
        val op = AppliedStringInsertOperation(fnameVID, false, 0, "abc")
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "abcjohn")
      }

      "correctly update the model on StringRemove" in withTestData { provider =>
        val op = AppliedStringRemoveOperation(fnameVID, false, 1, 2, Some("Oh"))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "jn")
      }

      "correctly update the model on StringSet" in withTestData { provider =>
        val op = AppliedStringSetOperation(fnameVID, false, "new string", Some("oldValue"))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "new string")
      }
    }

    "applying array operations" must {
      "correctly update the model on ArrayInsert" in withTestData { provider =>
        val insertVal = ObjectValue("pp1-f1", Map("field1" -> StringValue("pp1-sv", "someValue"), "field2" -> DoubleValue("pp1-5", 5)))
        val op = AppliedArrayInsertOperation(emailsVID, false, 0, insertVal)
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(emailsField) match {
          case ArrayValue(vid, children) => {
            children(0) shouldBe insertVal
          }
          case _ => fail
        }
      }

      "correctly update the model on ArrayRemove" in withTestData { provider =>
        val op = AppliedArrayRemoveOperation(emailsVID, false, 0, Some(StringValue("oldId", "removedValue")))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(emailsField) match {
          case ArrayValue(vid, children) => {
            children.size shouldBe 2
          }
          case _ => fail
        }
      }

      "correctly update the model on ArrayReplace" in withTestData { provider =>
        val replaceVal = ObjectValue("art-data", Map("field1" -> StringValue("art-f1", "someValue"), "field2" -> DoubleValue("art-f2", 5)))
        val op = AppliedArrayReplaceOperation(emailsVID, false, 0, replaceVal, Some(StringValue("oldId", "removedValue")))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(emailsField) match {
          case ArrayValue(vid, children) => {
            children(0) shouldBe replaceVal
            children.size shouldBe 3
          }
          case _ => fail
        }
      }

      "correctly update the model on ArrayMove" in withTestData { provider =>
        val op = AppliedArrayMoveOperation(emailsVID, false, 0, 2)
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(emailsField) shouldBe ArrayValue(emailsVID, List(
          StringValue("pp1-email2", "second@email.com"),
          StringValue("pp1-email3", "another@email.com"),
          StringValue("pp1-email1", "first@email.com")))
      }

      "correctly update the model on ArraySet" in withTestData { provider =>
        val setValue = List(StringValue("as-sv", "someValue"), StringValue("as-sov", "someOtherValue"))
        val op = AppliedArraySetOperation(emailsVID, false, setValue, Some(List[DataValue]()))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(emailsField) match {
          case ArrayValue(vid, children) => {
            children shouldEqual setValue
          }
          case _ => fail
        }
      }
    }

    "applying object operations" must {
      "correctly update the model on ObjectAddProperty" in withTestData { provider =>
        val op = AppliedObjectAddPropertyOperation(person1VID, false, "addedProperty", StringValue("aoo-value", "value"))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children("addedProperty") shouldEqual StringValue("aoo-value", "value")
      }

      "correctly update the model on ObjectAddProperty with a special char" in withTestData { provider =>
        val op = AppliedObjectAddPropertyOperation(person1VID, false, "prop-with-dash", StringValue("aoo-value", "value"))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children("prop-with-dash") shouldEqual StringValue("aoo-value", "value")
      }

      "correctly update the model on ObjectSetProperty" in withTestData { provider =>
        val op = AppliedObjectSetPropertyOperation(person1VID, false, fnameField, StringValue("pp1-fnbob", "bob"), Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldBe StringValue("pp1-fnbob", "bob")
      }

      "correctly update the model on ObjectRemoveProperty" in withTestData { provider =>
        val op = AppliedObjectRemovePropertyOperation(person1VID, false, fnameField, Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children.get(fnameField) shouldBe None
      }

      "correctly update the model on ObjectRemoveProperty with dash" in withTestData { provider =>
        val propWithDash = "prop-with-dash"

        val addOp = AppliedObjectAddPropertyOperation(person1VID, false, propWithDash, StringValue("aoo-value", "value"))
        provider.modelOperationProcessor.processModelOperation(NewModelOperation(person1Id, startingVersion, Instant.now(), sid, addOp)).get

        val op = AppliedObjectRemovePropertyOperation(person1VID, false, propWithDash, Some(StringValue("aoo-value", "value")))
        val modelOp = NewModelOperation(person1Id, startingVersion + 1, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children.get(propWithDash) shouldBe None
      }

      "correctly update the model on ObjectSet" in withTestData { provider =>
        val replacePerson = Map("fname" -> StringValue("pp1-fnbob", "bob"), "lname" -> StringValue("pp1-lnsmith", "smith"))
        val op = AppliedObjectSetOperation(person1VID, false, replacePerson, Some(Map("fname" -> StringValue("oldId1", "yo"), "lname" -> StringValue("oldId2", "yoyo"))))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children shouldBe replacePerson
      }
    }

    "applying number operations" must {
      "correctly update the model on NumberAdd" in withTestData { provider =>
        val op = AppliedNumberAddOperation(ageVID, false, 5)
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp)

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(ageField) shouldBe DoubleValue(ageVID, 31)
      }

      "correctly update the model on NumberSet" in withTestData { provider =>
        val op = AppliedNumberSetOperation(ageVID, false, 33, Some(22))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp)

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(ageField) shouldBe DoubleValue(ageVID, 33)
      }
    }

    "applying boolean operations" must {
      "correctly update the model on BooleanSet" in withTestData { provider =>
        val op = AppliedBooleanSetOperation(marriedVID, false, true, Some(false))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp)

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(marriedField) shouldBe BooleanValue(marriedVID, true)
      }
    }

    "applying date operations" must {
      "correctly update the model on DateSet" in withTestData { provider =>
        val newDate = Instant.now()
        val op = AppliedDateSetOperation(bornVID, false, newDate, Some(bornDate))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(bornField) shouldBe DateValue(bornVID, newDate)
      }
    }

    "handling non specialy object preoprty names" must {

      "correctly add property names that start with a period" in withTestData { provider =>
        val property = "my-prop!"
        val addOp = AppliedObjectAddPropertyOperation(person1VID, false, ".value", StringValue("aoo-value", "value"))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, addOp)

        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(".value") shouldBe StringValue("aoo-value", "value")
      }

      "correctly update property names that start with a period" in withTestData { provider =>
        val property = "my-prop!"
        val addOp = AppliedObjectAddPropertyOperation(person1VID, false, ".value", StringValue("aoo-value", "initial"))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, addOp)

        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(".value") shouldBe StringValue("aoo-value", "initial")

        val setOp = AppliedObjectSetPropertyOperation(person1VID, false, ".value", StringValue("aoo-value1", "updated"), Some(StringValue("aoo-value", "initial")))
        val setModelOp = NewModelOperation(person1Id, startingVersion + 1, Instant.now(), sid, setOp)

        provider.modelOperationProcessor.processModelOperation(setModelOp).get
        val updatedModelData = provider.modelStore.getModelData(person1Id).get.value
        updatedModelData.children(".value") shouldBe StringValue("aoo-value1", "updated")
      }

      "correctly handle property names in the path that are numeric" in withTestData { provider =>

        val property = "4"
        val op = AppliedObjectAddPropertyOperation(person1VID, false, property, StringValue("aoo-value", "value"))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(property) shouldBe StringValue("aoo-value", "value")
      }

      "correctly handle property names in the path that have a dash" in withTestData { provider =>
        val property = "a-dash"
        val op = AppliedObjectAddPropertyOperation(person1VID, false, property, StringValue("aoo-value", "value"))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(property) shouldBe StringValue("aoo-value", "value")
      }
    }

    "removing data" must {
      "remove a single object when issuing an ObjectSetProperty on a string field" in withTestData { provider =>
        vidExists(fnameVID, provider.dbProvider).get shouldBe true

        val op = AppliedObjectSetPropertyOperation(person1VID, false, fnameField, StringValue("pp1-fnbob", "bob"), Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidExists(fnameVID, provider.dbProvider).get shouldBe false
      }

      "remove recursively object when issuing an ObjectSetProperty on an array field" in withTestData { provider =>
        vidExists(emailsVID, provider.dbProvider).get shouldBe true
        vidExists(email1VID, provider.dbProvider).get shouldBe true
        vidExists(email2VID, provider.dbProvider).get shouldBe true
        vidExists(email3VID, provider.dbProvider).get shouldBe true

        val op = AppliedObjectSetPropertyOperation(person1VID, false, emailsField, StringValue("pp1-fnbob", "bob"), Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidExists(emailsVID, provider.dbProvider).get shouldBe false
        vidExists(email1VID, provider.dbProvider).get shouldBe false
        vidExists(email2VID, provider.dbProvider).get shouldBe false
        vidExists(email3VID, provider.dbProvider).get shouldBe false
      }

      "remove a single object when issuing an ObjectRemoveProperty on a string field" in withTestData { provider =>
        vidExists(fnameVID, provider.dbProvider).get shouldBe true

        val op = AppliedObjectRemovePropertyOperation(person1VID, false, fnameField, Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidExists(fnameVID, provider.dbProvider).get shouldBe false
      }

      "remove recursively object when issuing an ObjectRemoveProperty on an array field" in withTestData { provider =>
        vidExists(emailsVID, provider.dbProvider).get shouldBe true
        vidExists(email1VID, provider.dbProvider).get shouldBe true
        vidExists(email2VID, provider.dbProvider).get shouldBe true
        vidExists(email3VID, provider.dbProvider).get shouldBe true

        val op = AppliedObjectRemovePropertyOperation(person1VID, false, emailsField, Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidExists(emailsVID, provider.dbProvider).get shouldBe false
        vidExists(email1VID, provider.dbProvider).get shouldBe false
        vidExists(email2VID, provider.dbProvider).get shouldBe false
        vidExists(email3VID, provider.dbProvider).get shouldBe false
      }

      "Remove all previous object values in an ObjectSet" in withTestData { provider =>
        val childrenVids = person1Data.children.values.toList.map(_.id) ++ List(email1VID, email2VID, email2VID)
        vidsAllExist(childrenVids, provider.dbProvider).get shouldBe true

        val replacePerson = Map("fname" -> StringValue("pp1-fnbob", "bob"), "lname" -> StringValue("pp1-lnsmith", "smith"))
        val op = AppliedObjectSetOperation(person1VID, false, replacePerson, Some(Map("fname" -> StringValue("oldId1", "yo"), "lname" -> StringValue("oldId2", "yoyo"))))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidsNoneExist(childrenVids, provider.dbProvider).get shouldBe true
      }

      "Remove a datavalue on an ArrayRemove" in withTestData { provider =>
        vidsAllExist(List(email1VID, email2VID, email3VID), provider.dbProvider).get shouldBe true

        val op = AppliedArrayRemoveOperation(emailsVID, false, 0, Some(StringValue("oldId", "removedValue")))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidsAllExist(List(email2VID, email3VID), provider.dbProvider).get shouldBe true
        vidsNoneExist(List(email1VID), provider.dbProvider).get shouldBe true
      }

      "Remove a datavalue on an ArrayReplace" in withTestData { provider =>
        vidsAllExist(List(email1VID, email2VID, email3VID), provider.dbProvider).get shouldBe true

        val replaceVal = ObjectValue("art-data", Map("field1" -> StringValue("art-f1", "someValue"), "field2" -> DoubleValue("art-f2", 5)))
        val op = AppliedArrayReplaceOperation(emailsVID, false, 0, replaceVal, Some(StringValue("oldId", "removedValue")))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidsAllExist(List(email2VID, email3VID), provider.dbProvider).get shouldBe true
        vidsNoneExist(List(email1VID), provider.dbProvider).get shouldBe true
      }

      "Remove child datavalues on an ArraySet" in withTestData { provider =>
        vidExists(emailsVID, provider.dbProvider).get shouldBe true
        vidsAllExist(List(email1VID, email2VID, email3VID), provider.dbProvider).get shouldBe true

        val setValue = List(StringValue("as-sv", "someValue"), StringValue("as-sov", "someOtherValue"))
        val op = AppliedArraySetOperation(emailsVID, false, setValue, Some(List[DataValue]()))
        val modelOp = NewModelOperation(person1Id, startingVersion, Instant.now(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidExists(emailsVID, provider.dbProvider).get shouldBe true
        vidsNoneExist(List(email1VID, email2VID, email3VID), provider.dbProvider).get shouldBe true
      }
    }
  }

  def vidExists(vid: String, dbProvider: DatabaseProvider): Try[Boolean] = {
    dbProvider.withDatabase { db =>
      val query = "SELECT * FROM DataValue WHERE id = :id"
      val params = Map("id" -> vid)
      OrientDBUtil.getDocument(db, query, params).map(_ => true).orElse(Success(false))
    }
  }

  def vidsAllExist(vids: List[String], dbProvider: DatabaseProvider): Try[Boolean] = Try {
    vids.map(vid => vidExists(vid, dbProvider).get).forall(identity)
  }

  def vidsNoneExist(vids: List[String], dbProvider: DatabaseProvider): Try[Boolean] = Try {
    !vids.map(vid => vidExists(vid, dbProvider).get).exists(identity)
  }

  def withTestData(testCode: DomainPersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>
      provider.userStore.createDomainUser(user)
      provider.sessionStore.createSession(session)
      provider.collectionStore.ensureCollectionExists(peopleCollectionId)
      provider.modelStore.createModel(person1Model)
      testCode(provider)
    }
  }
}
