package com.convergencelabs.server.datastore.domain.mapper

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import ObjectSetOperationMapper.ObjectSetOperationToODocument
import ObjectSetOperationMapper.ODocumentToObjectSetOperation
import org.json4s.JsonAST.JObject

class ObjectSetOperationMapperSpec
    extends WordSpec
    with Matchers {

  val path = List(3, "foo", 4) // scalastyle:off magic.number

  "An ObjectSetOperationMapper" when {
    "when converting ObjectSetOperation operations" must {
      "correctly map and unmap a ObjectSetOperation" in {
        val op = ObjectSetOperation(path, true, JObject("foo" -> JString("test")))
        val opDoc = op.asODocument
        val reverted = opDoc.asObjectSetOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asObjectSetOperation
        }
      }
    }
  }
}
