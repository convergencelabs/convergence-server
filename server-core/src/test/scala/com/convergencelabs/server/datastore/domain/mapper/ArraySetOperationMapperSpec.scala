package com.convergencelabs.server.datastore.domain.mapper

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ArraySetOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import ArraySetOperationMapper.ArraySetOperationToODocument
import ArraySetOperationMapper.ODocumentToArraySetOperation
import org.json4s.JsonAST.JArray
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.ArrayValue
import com.convergencelabs.server.domain.model.data.DataValue

class ArraySetOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An ArraySetOperationMapper" when {
    "when converting ArraySetOperation operations" must {
      "correctly map and unmap a ArraySetOperation" in {
        val children: List[DataValue] = List(StringValue("asom-test", "test"))
        val op = ArraySetOperation("vid", true, ArrayValue("asom-array", children))
        val opDoc = op.asODocument
        val reverted = opDoc.asArraySetOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asArraySetOperation
        }
      }
    }
  }
}
