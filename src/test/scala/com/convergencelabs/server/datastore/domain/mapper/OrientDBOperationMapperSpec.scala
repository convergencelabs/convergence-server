package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.convergencelabs.server.domain.model.ot.AppliedCompoundOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation

class OrientDBOperationMapperSpec
    extends WordSpec
    with Matchers {

  val valueId = "vid"

  val jsonString = StringValue("jsonString", "A String")
  val jsonInt = DoubleValue("jsonInt", 4) // scalastyle:off magic.number

  val complexJsonArray = ArrayValue("complexJsonArray", List(
    StringValue("av-1", "A String"),
    DoubleValue("av-2", 2),
    BooleanValue("av-3", true),
    NullValue("av-4"),
    DoubleValue("av-5", 5),
    DoubleValue("av-6", 9),
    ObjectValue("av-7", Map("key" -> StringValue("av7-1", "value")))))

  val complexJsonObject = ObjectValue("complexJsonObject", Map(
    "array" -> complexJsonArray,
    "int" -> DoubleValue("ov-1", 4),
    "decimal" -> DoubleValue("ov-2", 6D),
    "double" -> DoubleValue("ov-3", 10D),
    "string" -> StringValue("ov-4", "another string"),
    "null" -> NullValue("ov-5"),
    "boolean" -> BooleanValue("ov-6", false),
    "object" -> ObjectValue("ov-7", Map("something" -> DoubleValue("ov7-1", 2)))))

  "An OrientDBOperationMapper" when {
    "converting string operations" must {
      "correctly map and unmap an StringInsertOperation" in {
        val op = AppliedStringInsertOperation(valueId, true, 3, "inserted")
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an StringRemoveOperation" in {
        val op = AppliedStringRemoveOperation(valueId, true, 3, 7, Some("removed"))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an StringSetOperation" in {
        val op = AppliedStringSetOperation(valueId, true, "something", Some("old"))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting array operations" must {
      "correctly map and unmap an ArrayInsertOperation" in {
        val op = AppliedArrayInsertOperation(valueId, true, 3, complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayRemoveOperation" in {
        val op = AppliedArrayRemoveOperation(valueId, true, 3, Some(StringValue("oldId", "oldValue")))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayReplaceOperation" in {
        val op = AppliedArrayReplaceOperation(valueId, true, 3, complexJsonArray, Some(StringValue("oldId", "oldValue")))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayMoveOperation" in {
        val op = AppliedArrayMoveOperation(valueId, true, 3, 5)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArraySetOperation" in {
        val op = AppliedArraySetOperation(valueId, true, complexJsonArray.children, Some(List[DataValue]()))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting object operations" must {
      "correctly map and unmap an ObjectSetPropertyOperation" in {
        val op = AppliedObjectSetPropertyOperation(valueId, true, "setProp", complexJsonObject, Some(StringValue("oldId", "oldValue")))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectAddPropertyOperation" in {
        val op = AppliedObjectAddPropertyOperation(valueId, true, "addProp", complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectRemovePropertyOperation" in {
        val op = AppliedObjectRemovePropertyOperation(valueId, true, "remvoveProp", Some(StringValue("oldId", "oldValue")))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectSetOperation" in {
        val op = AppliedObjectSetOperation(valueId, true, complexJsonObject.children, Some(Map[String, DataValue]()))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting number operations" must {
      "correctly map and unmap an NumberAddOperation" in {
        val op = AppliedNumberAddOperation(valueId, true, 1)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an NumberSetOperation" in {
        val op = AppliedNumberSetOperation(valueId, true, 1, Some(3))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting compound operations" must {
      "correctly map and unmap a CompoundOperation" in {
        val ops = List(
          AppliedObjectSetOperation(valueId, true, complexJsonObject.children, Some(Map[String, DataValue]())),
          AppliedArrayRemoveOperation("vid2", true, 3, Some(StringValue("oldId", "oldValue"))))

        val op = AppliedCompoundOperation(ops)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }
  }
}
