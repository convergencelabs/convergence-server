package com.convergencelabs.server.datastore.domain.mapper

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JInt
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.BooleanSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import BooleanSetOperationMapper.BooleanSetOperationToODocument
import BooleanSetOperationMapper.ODocumentToBooleanSetOperation

class BooleanSetOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An BooleanSetOperationMapper" when {
    "when converting BooleanSetOperation operations" must {
      "correctly map and unmap a BooleanSetOperation" in {
        val op = BooleanSetOperation("vid", true, true)
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
