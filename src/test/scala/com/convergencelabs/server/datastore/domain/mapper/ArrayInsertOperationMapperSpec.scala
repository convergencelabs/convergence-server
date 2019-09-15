package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayInsertOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ArrayInsertOperationMapper.ArrayInsertOperationToODocument
import ArrayInsertOperationMapper.ODocumentToArrayInsertOperation

class ArrayInsertOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An ArrayInsertOperationMapper" when {
    "when converting ArrayInsertOperation operations" must {
      "correctly map and unmap a ArrayInsertOperation" in {
        val op = AppliedArrayInsertOperation("vid", true, 4, StringValue("aiom-test", "test")) // scalastyle:ignore magic.number
        val opDoc = op.asODocument
        val reverted = opDoc.asArrayInsertOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asArrayInsertOperation
        }
      }
    }
  }
}
