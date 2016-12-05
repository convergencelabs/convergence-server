package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ObjectAddPropertyOperationMapper.ODocumentToObjectAddPropertyOperation
import ObjectAddPropertyOperationMapper.ObjectAddPropertyOperationToODocument

class ObjectAddPropertyOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An ObjectAddPropertyOperationMapper" when {
    "when converting ObjectAddPropertyOperation operations" must {
      "correctly map and unmap a ObjectAddPropertyOperation" in {
        val op = AppliedObjectAddPropertyOperation("vid", true, "foo", StringValue("vid1", "bar"))
        val opDoc = op.asODocument
        val reverted = opDoc.asObjectAddPropertyOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asObjectAddPropertyOperation
        }
      }
    }
  }
}
