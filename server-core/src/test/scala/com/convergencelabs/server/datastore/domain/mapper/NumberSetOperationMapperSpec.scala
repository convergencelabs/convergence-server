package com.convergencelabs.server.datastore.domain.mapper

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JInt
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.NumberSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import NumberSetOperationMapper.NumberSetOperationToODocument
import NumberSetOperationMapper.ODocumentToNumberSetOperation

class NumberSetOperationMapperSpec
    extends WordSpec
    with Matchers {

  val path = List(3, "foo", 4) // scalastyle:off magic.number

  "An NumberSetOperationMapper" when {
    "when converting NumberSetOperation operations" must {
      "correctly map and unmap a NumberSetOperation" in {
        val op = NumberSetOperation(path, true, JInt(4))
        val opDoc = op.asODocument
        val reverted = opDoc.asNumberSetOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asNumberSetOperation
        }
      }
    }
  }
}
