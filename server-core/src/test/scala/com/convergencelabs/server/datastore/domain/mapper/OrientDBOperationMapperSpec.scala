package com.convergencelabs.server.datastore.domain.mapper

import scala.BigDecimal
import scala.math.BigDecimal.double2bigDecimal
import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JBool
import org.json4s.JsonAST.JDecimal
import org.json4s.JsonAST.JDouble
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JNull
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JString
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

class OrientDBOperationMapperSpec
    extends WordSpec
    with Matchers {

  val path = List(3, "foo", 4) // scalastyle:off magic.number

  val jsonString = JString("A String")
  val jsonInt = JInt(4) // scalastyle:off magic.number

  val complexJsonArray = JArray(List(
    JString("A String"),
    JInt(2),
    JBool(true),
    JNull,
    JDecimal(BigDecimal(5D)),
    JDouble(9D),
    JObject("key" -> JString("value"))))

  val complexJsonObject = JObject(
    "array" -> complexJsonArray,
    "int" -> JInt(4),
    "decimal" -> JDecimal(6D),
    "double" -> JDouble(10D),
    "string" -> JString("another string"),
    "null" -> JNull,
    "boolean" -> JBool(false),
    "object" -> JObject("something" -> JInt(2)))

  "An OrientDBOperationMapper" when {
    "when converting string operations" must {
      "correctly map and unmap an StringInsertOperation" in {
        val op = StringInsertOperation(path, true, 3, "inserted")
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an StringRemoveOperation" in {
        val op = StringRemoveOperation(path, true, 3, "removed")
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an StringSetOperation" in {
        val op = StringSetOperation(path, true, "something")
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "when converting array operations" must {
      "correctly map and unmap an ArrayInsertOperation" in {
        val op = ArrayInsertOperation(path, true, 3, complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayRemoveOperation" in {
        val op = ArrayRemoveOperation(path, true, 3)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayReplaceOperation" in {
        val op = ArrayReplaceOperation(path, true, 3, complexJsonArray)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArrayMoveOperation" in {
        val op = ArrayMoveOperation(path, true, 3, 5)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ArraySetOperation" in {
        val op = ArraySetOperation(path, true, complexJsonArray)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "when converting object operations" must {
      "correctly map and unmap an ObjectSetPropertyOperation" in {
        val op = ObjectSetPropertyOperation(path, true, "setProp", complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectAddPropertyOperation" in {
        val op = ObjectAddPropertyOperation(path, true, "addProp", complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectRemovePropertyOperation" in {
        val op = ObjectRemovePropertyOperation(path, true, "remvoveProp")
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an ObjectSetOperation" in {
        val op = ObjectSetOperation(path, true, complexJsonObject)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "when converting number operations" must {
      "correctly map and unmap an NumberAddOperation" in {
        val op = NumberAddOperation(path, true, JInt(1))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }

      "correctly map and unmap an NumberSetOperation" in {
        val op = NumberSetOperation(path, true, JInt(1))
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }

    "when converting compound operations" must {
      "correctly map and unmap a CompoundOperation" in {
        val ops = List(
          ObjectSetOperation(path, true, complexJsonObject),
          ArrayRemoveOperation(path, true, 3))

        val op = CompoundOperation(ops)
        val asDoc = OrientDBOperationMapper.operationToODocument(op)
        val reverted = OrientDBOperationMapper.oDocumentToOperation(asDoc)
        reverted shouldBe op
      }
    }
  }
}
