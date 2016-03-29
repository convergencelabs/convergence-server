package com.convergencelabs.server.datastore.domain.mapper

import scala.BigDecimal
import scala.math.BigDecimal.double2bigDecimal
import scala.math.BigInt.int2bigInt
import org.scalatest.Finders
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import org.scalatest.Matchers
import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.BooleanValue
import com.convergencelabs.server.domain.model.data.NullValue
import com.convergencelabs.server.domain.model.data.DoubleValue
import com.convergencelabs.server.domain.model.data.ObjectValue

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
        val op = StringInsertOperation(valueId, true, 3, "inserted")
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an StringRemoveOperation" in {
        val op = StringRemoveOperation(valueId, true, 3, "removed")
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an StringSetOperation" in {
        val op = StringSetOperation(valueId, true, "something")
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting array operations" must {
      "correctly map and unmap an ArrayInsertOperation" in {
        val op = ArrayInsertOperation(valueId, true, 3, complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayRemoveOperation" in {
        val op = ArrayRemoveOperation(valueId, true, 3)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayReplaceOperation" in {
        val op = ArrayReplaceOperation(valueId, true, 3, complexJsonArray)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayMoveOperation" in {
        val op = ArrayMoveOperation(valueId, true, 3, 5)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArraySetOperation" in {
        val op = ArraySetOperation(valueId, true, complexJsonArray.children)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting object operations" must {
      "correctly map and unmap an ObjectSetPropertyOperation" in {
        val op = ObjectSetPropertyOperation(valueId, true, "setProp", complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectAddPropertyOperation" in {
        val op = ObjectAddPropertyOperation(valueId, true, "addProp", complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectRemovePropertyOperation" in {
        val op = ObjectRemovePropertyOperation(valueId, true, "remvoveProp")
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectSetOperation" in {
        val op = ObjectSetOperation(valueId, true, complexJsonObject.children)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting number operations" must {
      "correctly map and unmap an NumberAddOperation" in {
        val op = NumberAddOperation(valueId, true, 1)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an NumberSetOperation" in {
        val op = NumberSetOperation(valueId, true, 1)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "converting compound operations" must {
      "correctly map and unmap a CompoundOperation" in {
        val ops = List(
          ObjectSetOperation(valueId, true, complexJsonObject.children),
          ArrayRemoveOperation("vid2", true, 3))

        val op = CompoundOperation(ops)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }
  }
}
