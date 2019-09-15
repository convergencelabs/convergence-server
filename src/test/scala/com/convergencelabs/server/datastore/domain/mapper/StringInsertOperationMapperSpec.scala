package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import StringInsertOperationMapper.ODocumentToStringInsertOperation
import StringInsertOperationMapper.StringInsertOperationToODocument

class StringInsertOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An StringInsertOperationMapper" when {
    "when converting StringInsertOperation operations" must {
      "correctly map and unmap a StringInsertOperation" in {
        val op = AppliedStringInsertOperation("vid", true, 4, "test") // scalastyle:ignore magic.number
        val opDoc = op.asODocument
        val reverted = opDoc.asStringInsertOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asStringInsertOperation
        }
      }
    }
  }
}
