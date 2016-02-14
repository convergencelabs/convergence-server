package com.convergencelabs.server.datastore.domain.mapper

import org.json4s.JsonAST.JString
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.ArrayReplaceOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ArrayReplaceOperationMapper.ArrayReplaceOperationToODocument
import ArrayReplaceOperationMapper.ODocumentToArrayReplaceOperation

class ArrayReplaceOperationMapperSpec
    extends WordSpec
    with Matchers {

  val path = List(3, "foo", 4) // scalastyle:off magic.number

  "An ArrayReplaceOperationMapper" when {
    "when converting ArrayReplaceOperation operations" must {
      "correctly map and unmap a ArrayReplaceOperation" in {
        val op = ArrayReplaceOperation(path, true, 4, JString("test"))
        val opDoc = op.asODocument
        val reverted = opDoc.asArrayReplaceOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asArrayReplaceOperation
        }
      }
    }
  }
}
