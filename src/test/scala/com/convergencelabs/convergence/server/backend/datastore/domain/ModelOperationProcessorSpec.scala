/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.domain

import java.time.{Duration, Instant}

import com.convergencelabs.convergence.server.backend.datastore.OrientDBUtil
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.schema.NonRecordingSchemaManager
import com.convergencelabs.convergence.server.backend.services.domain.model.NewModelOperation
import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.model._
import com.convergencelabs.convergence.server.model.domain.session.DomainSession
import com.convergencelabs.convergence.server.model.domain.user.{DomainUser, DomainUserId, DomainUserType}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.language.postfixOps
import scala.util.{Success, Try}

// scalastyle:off magic.number multiple.string.literals
class ModelOperationProcessorSpec
  extends PersistenceStoreSpec[DomainPersistenceProvider](NonRecordingSchemaManager.SchemaType.Domain)
  with AnyWordSpecLike
  with OptionValues
  with Matchers {

  private val username = "test"
  private val user = DomainUser(DomainUserType.Normal, username, None, None, None, None, None)

  private val sid = "u1-1"
  private val session = DomainSession(sid, DomainUserId.normal(username), truncatedInstantNow(), None, "jwt", "js", "1.0", "", "127.0.0.1")

  private val modelPermissions = ModelPermissions(read = true, write = true, remove = true, manage = true)

  private val startingVersion = 100

  private val peopleCollectionId = "people"
  private val person1Id = "person1"
  private val person1MetaData = ModelMetaData(
    person1Id,
    peopleCollectionId,
    startingVersion,
    truncatedInstantNow(),
    truncatedInstantNow(),
    overridePermissions = true,
    modelPermissions,
    1)

  private val person1VID = "pp1-data"
  private val fnameVID = "pp1-fname"
  private val lnameVID = "pp1-lname"
  private val emailsVID = "pp1-emails"
  private val ageVID = "pp1-age"
  private val bornVID = "pp1-born"
  private val marriedVID = "pp1-married"
  private val email1VID = "pp1-email1"
  private val email2VID = "pp1-email2"
  private val email3VID = "pp1-email3"
  private val spouseVID = "pp1-spouse"

  private val fnameField = "fname"
  private val lnameField = "lname"
  private val emailsField = "emails"
  private val ageField = "age"
  private val spouseField = "spouse"
  private val marriedField = "married"
  private val bornField = "born"

  private val bornDate: Instant = truncatedInstantNow().minus(Duration.ofDays(6000))

  private val person1Data = ObjectValue(person1VID, Map(
    fnameField -> StringValue(fnameVID, "john"),
    lnameField -> StringValue(lnameVID, "smith"),
    ageField -> DoubleValue(ageVID, 26),
    bornField -> DateValue(bornVID, bornDate),
    marriedField -> BooleanValue(marriedVID, value = false),
    spouseField -> NullValue(spouseVID),
    emailsField -> ArrayValue(emailsVID, List(
      StringValue(email1VID, "first@email.com"),
      StringValue(email2VID, "second@email.com"),
      StringValue(email3VID, "another@email.com")))))
  private val person1Model = Model(person1MetaData, person1Data)

  def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider = new DomainPersistenceProviderImpl(DomainId("namespace", "domain"), dbProvider)

  "A ModelOperationProcessor" when {

    "applying a noOp'ed discrete operation" must {
      "not apply the operation" in withTestData { provider =>
        val op = AppliedStringInsertOperation(fnameVID, noOp = true, 0, "abc")
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "john")
      }
    }

    "applying a compound operation" must {
      "apply all operations in the compound operation" in withTestData { provider =>
        val op1 = AppliedStringInsertOperation(fnameVID, noOp = false, 0, "x")
        val op2 = AppliedStringInsertOperation(fnameVID, noOp = false, 1, "y")

        val compound = AppliedCompoundOperation(List(op1, op2))

        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, compound)
        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "xyjohn")
      }

      "apply all operations in rename compound operation" in withTestData { provider =>
        val op1 = AppliedObjectRemovePropertyOperation(person1VID, noOp = false, lnameField, Some(StringValue("oldId", "oldValue")))
        val op2 = AppliedObjectAddPropertyOperation(person1VID, noOp = false, "newName", StringValue("idididi", "somethingelse"))

        val compound = AppliedCompoundOperation(List(op1, op2))

        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, compound)
        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children("newName") shouldEqual StringValue("idididi", "somethingelse")
      }

      "not apply noOp'ed operations in the compound operation" in withTestData { provider =>
        val op1 = AppliedStringInsertOperation(fnameVID, noOp = false, 0, "x")
        val op2 = AppliedStringInsertOperation(fnameVID, noOp = true, 1, "y")

        val compound = AppliedCompoundOperation(List(op1, op2))

        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, compound)
        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "xjohn")
      }
    }

    "applying string operations" must {
      "correctly update the model on StringInsert" in withTestData { provider =>
        val op = AppliedStringInsertOperation(fnameVID, noOp = false, 0, "abc")
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "abcjohn")
      }

      "correctly update the model on StringRemove" in withTestData { provider =>
        val op = AppliedStringRemoveOperation(fnameVID, noOp = false, 1, 2, Some("Oh"))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "jn")
      }

      "correctly update the model on StringSet" in withTestData { provider =>
        val op = AppliedStringSetOperation(fnameVID, noOp = false, "new string", Some("oldValue"))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldEqual StringValue(fnameVID, "new string")
      }
    }

    "applying array operations" must {
      "correctly update the model on ArrayInsert" in withTestData { provider =>
        val insertVal = ObjectValue("pp1-f1", Map("field1" -> StringValue("pp1-sv", "someValue"), "field2" -> DoubleValue("pp1-5", 5)))
        val op = AppliedArrayInsertOperation(emailsVID, noOp = false, 0, insertVal)
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(emailsField) match {
          case ArrayValue(_, children) =>
            children.head shouldBe insertVal
          case _ => fail
        }
      }

      "correctly update the model on ArrayRemove" in withTestData { provider =>
        val op = AppliedArrayRemoveOperation(emailsVID, noOp = false, 0, Some(StringValue("oldId", "removedValue")))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(emailsField) match {
          case ArrayValue(_, children) =>
            children.size shouldBe 2
          case _ => fail
        }
      }

      "correctly update the model on ArrayReplace" in withTestData { provider =>
        val replaceVal = ObjectValue("art-data", Map("field1" -> StringValue("art-f1", "someValue"), "field2" -> DoubleValue("art-f2", 5)))
        val op = AppliedArrayReplaceOperation(emailsVID, noOp = false, 0, replaceVal, Some(StringValue("oldId", "removedValue")))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(emailsField) match {
          case ArrayValue(_, children) =>
            children.head shouldBe replaceVal
            children.size shouldBe 3
          case _ => fail
        }
      }

      "correctly update the model on ArrayMove" in withTestData { provider =>
        val op = AppliedArrayMoveOperation(emailsVID, noOp = false, 0, 2)
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(emailsField) shouldBe ArrayValue(emailsVID, List(
          StringValue("pp1-email2", "second@email.com"),
          StringValue("pp1-email3", "another@email.com"),
          StringValue("pp1-email1", "first@email.com")))
      }

      "correctly update the model on ArraySet" in withTestData { provider =>
        val setValue = List(StringValue("as-sv", "someValue"), StringValue("as-sov", "someOtherValue"))
        val op = AppliedArraySetOperation(emailsVID, noOp = false, setValue, Some(List[DataValue]()))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(emailsField) match {
          case ArrayValue(_, children) =>
            children shouldEqual setValue
          case _ => fail
        }
      }
    }

    "applying object operations" must {
      "correctly update the model on ObjectAddProperty" in withTestData { provider =>
        val op = AppliedObjectAddPropertyOperation(person1VID, noOp = false, "addedProperty", StringValue("aoo-value", "value"))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children("addedProperty") shouldEqual StringValue("aoo-value", "value")
      }

      "correctly update the model on ObjectAddProperty with a special char" in withTestData { provider =>
        val op = AppliedObjectAddPropertyOperation(person1VID, noOp = false, "prop-with-dash", StringValue("aoo-value", "value"))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children("prop-with-dash") shouldEqual StringValue("aoo-value", "value")
      }

      "correctly update the model on ObjectSetProperty" in withTestData { provider =>
        val op = AppliedObjectSetPropertyOperation(person1VID, noOp = false, fnameField, StringValue("pp1-fnbob", "bob"), Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(fnameField) shouldBe StringValue("pp1-fnbob", "bob")
      }

      "correctly update the model on ObjectRemoveProperty" in withTestData { provider =>
        val op = AppliedObjectRemovePropertyOperation(person1VID, noOp = false, fnameField, Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children.get(fnameField) shouldBe None
      }

      "correctly update the model on ObjectRemoveProperty with dash" in withTestData { provider =>
        val propWithDash = "prop-with-dash"

        val addOp = AppliedObjectAddPropertyOperation(person1VID, noOp = false, propWithDash, StringValue("aoo-value", "value"))
        provider.modelOperationProcessor.processModelOperation(NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, addOp)).get

        val op = AppliedObjectRemovePropertyOperation(person1VID, noOp = false, propWithDash, Some(StringValue("aoo-value", "value")))
        val modelOp = NewModelOperation(person1Id, startingVersion + 1, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children.get(propWithDash) shouldBe None
      }

      "correctly update the model on ObjectSet" in withTestData { provider =>
        val replacePerson = Map("fname" -> StringValue("pp1-fnbob", "bob"), "lname" -> StringValue("pp1-lnsmith", "smith"))
        val op = AppliedObjectSetOperation(person1VID, noOp = false, replacePerson, Some(Map("fname" -> StringValue("oldId1", "yo"), "lname" -> StringValue("oldId2", "yoyo"))))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children shouldBe replacePerson
      }
    }

    "applying number operations" must {
      "correctly update the model on NumberAdd" in withTestData { provider =>
        val op = AppliedNumberAddOperation(ageVID, noOp = false, 5)
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp)

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(ageField) shouldBe DoubleValue(ageVID, 31)
      }

      "correctly update the model on NumberSet" in withTestData { provider =>
        val op = AppliedNumberSetOperation(ageVID, noOp = false, 33, Some(22))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp)

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(ageField) shouldBe DoubleValue(ageVID, 33)
      }
    }

    "applying boolean operations" must {
      "correctly update the model on BooleanSet" in withTestData { provider =>
        val op = AppliedBooleanSetOperation(marriedVID, noOp = false, value = true, Some(false))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp)

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(marriedField) shouldBe BooleanValue(marriedVID, value = true)
      }
    }

    "applying date operations" must {
      "correctly update the model on DateSet" in withTestData { provider =>
        val newDate = truncatedInstantNow()
        val op = AppliedDateSetOperation(bornVID, noOp = false, newDate, Some(bornDate))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(bornField) shouldBe DateValue(bornVID, newDate)
      }
    }

    "handling non specialy object preoprty names" must {

      "correctly add property names that start with a period" in withTestData { provider =>
        val addOp = AppliedObjectAddPropertyOperation(person1VID, noOp = false, ".value", StringValue("aoo-value", "value"))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, addOp)

        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(".value") shouldBe StringValue("aoo-value", "value")
      }

      "correctly update property names that start with a period" in withTestData { provider =>
        val addOp = AppliedObjectAddPropertyOperation(person1VID, noOp = false, ".value", StringValue("aoo-value", "initial"))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, addOp)

        provider.modelOperationProcessor.processModelOperation(modelOp).get
        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(".value") shouldBe StringValue("aoo-value", "initial")

        val setOp = AppliedObjectSetPropertyOperation(person1VID, noOp = false, ".value", StringValue("aoo-value1", "updated"), Some(StringValue("aoo-value", "initial")))
        val setModelOp = NewModelOperation(person1Id, startingVersion + 1, truncatedInstantNow(), sid, setOp)

        provider.modelOperationProcessor.processModelOperation(setModelOp).get
        val updatedModelData = provider.modelStore.getModelData(person1Id).get.value
        updatedModelData.children(".value") shouldBe StringValue("aoo-value1", "updated")
      }

      "correctly handle property names in the path that are numeric" in withTestData { provider =>

        val property = "4"
        val op = AppliedObjectAddPropertyOperation(person1VID, noOp = false, property, StringValue("aoo-value", "value"))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(property) shouldBe StringValue("aoo-value", "value")
      }

      "correctly handle property names in the path that have a dash" in withTestData { provider =>
        val property = "a-dash"
        val op = AppliedObjectAddPropertyOperation(person1VID, noOp = false, property, StringValue("aoo-value", "value"))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        val modelData = provider.modelStore.getModelData(person1Id).get.value
        modelData.children(property) shouldBe StringValue("aoo-value", "value")
      }
    }

    "removing data" must {
      "remove a single object when issuing an ObjectSetProperty on a string field" in withTestData { provider =>
        vidExists(fnameVID, provider.dbProvider).get shouldBe true

        val op = AppliedObjectSetPropertyOperation(person1VID, noOp = false, fnameField, StringValue("pp1-fnbob", "bob"), Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidExists(fnameVID, provider.dbProvider).get shouldBe false
      }

      "remove recursively object when issuing an ObjectSetProperty on an array field" in withTestData { provider =>
        vidExists(emailsVID, provider.dbProvider).get shouldBe true
        vidExists(email1VID, provider.dbProvider).get shouldBe true
        vidExists(email2VID, provider.dbProvider).get shouldBe true
        vidExists(email3VID, provider.dbProvider).get shouldBe true

        val op = AppliedObjectSetPropertyOperation(person1VID, noOp = false, emailsField, StringValue("pp1-fnbob", "bob"), Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidExists(emailsVID, provider.dbProvider).get shouldBe false
        vidExists(email1VID, provider.dbProvider).get shouldBe false
        vidExists(email2VID, provider.dbProvider).get shouldBe false
        vidExists(email3VID, provider.dbProvider).get shouldBe false
      }

      "remove a single object when issuing an ObjectRemoveProperty on a string field" in withTestData { provider =>
        vidExists(fnameVID, provider.dbProvider).get shouldBe true

        val op = AppliedObjectRemovePropertyOperation(person1VID, noOp = false, fnameField, Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidExists(fnameVID, provider.dbProvider).get shouldBe false
      }

      "remove recursively object when issuing an ObjectRemoveProperty on an array field" in withTestData { provider =>
        vidExists(emailsVID, provider.dbProvider).get shouldBe true
        vidExists(email1VID, provider.dbProvider).get shouldBe true
        vidExists(email2VID, provider.dbProvider).get shouldBe true
        vidExists(email3VID, provider.dbProvider).get shouldBe true

        val op = AppliedObjectRemovePropertyOperation(person1VID, noOp = false, emailsField, Some(StringValue("oldId", "oldVal")))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
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
        val op = AppliedObjectSetOperation(person1VID, noOp = false, replacePerson, Some(Map("fname" -> StringValue("oldId1", "yo"), "lname" -> StringValue("oldId2", "yoyo"))))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidsNoneExist(childrenVids, provider.dbProvider).get shouldBe true
      }

      "Remove a datavalue on an ArrayRemove" in withTestData { provider =>
        vidsAllExist(List(email1VID, email2VID, email3VID), provider.dbProvider).get shouldBe true

        val op = AppliedArrayRemoveOperation(emailsVID, noOp = false, 0, Some(StringValue("oldId", "removedValue")))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidsAllExist(List(email2VID, email3VID), provider.dbProvider).get shouldBe true
        vidsNoneExist(List(email1VID), provider.dbProvider).get shouldBe true
      }

      "Remove a datavalue on an ArrayReplace" in withTestData { provider =>
        vidsAllExist(List(email1VID, email2VID, email3VID), provider.dbProvider).get shouldBe true

        val replaceVal = ObjectValue("art-data", Map("field1" -> StringValue("art-f1", "someValue"), "field2" -> DoubleValue("art-f2", 5)))
        val op = AppliedArrayReplaceOperation(emailsVID, noOp = false, 0, replaceVal, Some(StringValue("oldId", "removedValue")))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
        provider.modelOperationProcessor.processModelOperation(modelOp).get

        vidsAllExist(List(email2VID, email3VID), provider.dbProvider).get shouldBe true
        vidsNoneExist(List(email1VID), provider.dbProvider).get shouldBe true
      }

      "Remove child datavalues on an ArraySet" in withTestData { provider =>
        vidExists(emailsVID, provider.dbProvider).get shouldBe true
        vidsAllExist(List(email1VID, email2VID, email3VID), provider.dbProvider).get shouldBe true

        val setValue = List(StringValue("as-sv", "someValue"), StringValue("as-sov", "someOtherValue"))
        val op = AppliedArraySetOperation(emailsVID, noOp = false, setValue, Some(List[DataValue]()))
        val modelOp = NewModelOperation(person1Id, startingVersion, truncatedInstantNow(), sid, op)
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
