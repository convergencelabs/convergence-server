package com.convergencelabs.server.datastore.domain.mapper

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JInt
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.NumberAddOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import NumberAddOperationMapper.NumberAddOperationToODocument
import NumberAddOperationMapper.ODocumentToNumberAddOperation

class NumberAddOperationMapperSpec
    extends WordSpec
    with Matchers {

  val path = List(3, "foo", 4) // scalastyle:off magic.number

  "An NumberAddOperationMapper" when {
    "when converting NumberAddOperation operations" must {
      "correctly map and unmap a NumberAddOperation" in {
        val op = NumberAddOperation(path, true, JInt(4))
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
