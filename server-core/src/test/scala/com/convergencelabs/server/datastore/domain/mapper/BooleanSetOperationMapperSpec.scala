package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.AppliedBooleanSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import BooleanSetOperationMapper.BooleanSetOperationToODocument
import BooleanSetOperationMapper.ODocumentToBooleanSetOperation

class BooleanSetOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An BooleanSetOperationMapper" when {
    "when converting BooleanSetOperation operations" must {
      "correctly map and unmap a BooleanSetOperation" in {
        val op = AppliedBooleanSetOperation("vid", true, true, Some(false))
        val opDoc = op.asODocument
        val reverted = opDoc.asBooleanSetOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asBooleanSetOperation
        }
      }
    }
  }
}
