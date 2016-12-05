package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ArrayRemoveOperationMapper.ArrayRemoveOperationToODocument
import ArrayRemoveOperationMapper.ODocumentToArrayRemoveOperation

class ArrayRemoveOperationMapperSpec
    extends WordSpec
    with Matchers {


  "An ArrayRemoveOperationMapper" when {
    "when converting ArrayRemoveOperation operations" must {
      "correctly map and unmap a ArrayRemoveOperation" in {
        val op = AppliedArrayRemoveOperation("vid", true, 4, Some(StringValue("oldId", "oldValue"))) // scalastyle:ignore magic.number
        val opDoc = op.asODocument
        val reverted = opDoc.asArrayRemoveOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asArrayRemoveOperation
        }
      }
    }
  }
}
