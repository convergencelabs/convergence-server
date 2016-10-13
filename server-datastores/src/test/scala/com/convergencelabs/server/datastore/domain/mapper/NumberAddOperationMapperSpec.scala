package com.convergencelabs.server.datastore.domain.mapper

import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JInt
import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.orientechnologies.orient.core.record.impl.ODocument
import NumberAddOperationMapper.NumberAddOperationToODocument
import NumberAddOperationMapper.ODocumentToNumberAddOperation
import org.json4s.JsonAST.JDouble
import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation

class NumberAddOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An NumberAddOperationMapper" when {
    "when converting NumberAddOperation operations" must {
      "correctly map and unmap a NumberAddOperation" in {
        val op = AppliedNumberAddOperation("vid", true, 4) // scalastyle:ignore magic.number
        val opDoc = op.asODocument
        val reverted = opDoc.asNumberAddOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asNumberAddOperation
        }
      }
    }
  }
}
