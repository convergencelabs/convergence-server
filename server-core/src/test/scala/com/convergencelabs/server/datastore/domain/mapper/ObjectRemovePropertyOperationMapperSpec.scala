package com.convergencelabs.server.datastore.domain.mapper

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ObjectRemovePropertyOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import ObjectRemovePropertyOperationMapper.ObjectRemovePropertyOperationToODocument
import ObjectRemovePropertyOperationMapper.ODocumentToObjectRemovePropertyOperation
import org.json4s.JsonAST.JObject

class ObjectRemovePropertyOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An ObjectRemovePropertyOperationMapper" when {
    "when converting ObjectRemovePropertyOperation operations" must {
      "correctly map and unmap a ObjectRemovePropertyOperation" in {
        val op = ObjectRemovePropertyOperation("vid", true, "foo")
        val opDoc = op.asODocument
        val reverted = opDoc.asObjectRemovePropertyOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asObjectRemovePropertyOperation
        }
      }
    }
  }
}
