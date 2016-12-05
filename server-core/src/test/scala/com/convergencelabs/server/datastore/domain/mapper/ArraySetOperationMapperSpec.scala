package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.data.DataValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedArraySetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ArraySetOperationMapper.ArraySetOperationToODocument
import ArraySetOperationMapper.ODocumentToArraySetOperation

class ArraySetOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An ArraySetOperationMapper" when {
    "when converting ArraySetOperation operations" must {
      "correctly map and unmap a ArraySetOperation" in {
        val oldChildren: List[DataValue] = List(StringValue("asom-test-old", "oldString"))
        val children: List[DataValue] = List(StringValue("asom-test", "test"))
        val op = AppliedArraySetOperation("vid", true, children, Some(oldChildren))
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
