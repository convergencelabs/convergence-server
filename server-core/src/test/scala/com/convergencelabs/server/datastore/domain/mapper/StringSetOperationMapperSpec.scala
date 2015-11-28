package com.convergencelabs.server.datastore.domain.mapper

import org.json4s.JsonAST.JString
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.StringSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import StringSetOperationMapper.StringSetOperationToODocument
import StringSetOperationMapper.ODocumentToStringSetOperation

class StringSetOperationMapperSpec
    extends WordSpec
    with Matchers {

  val path = List(3, "foo", 4)

  "An StringSetOperationMapper" when {
    "when converting StringSetOperation operations" must {
      "correctly map and unmap a StringSetOperation" in {
        val op = StringSetOperation(path, true, "test")
        val opDoc = op.asODocument
        val reverted = opDoc.asStringSetOperation
        op shouldBe reverted
      }
      
      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asStringSetOperation
        }
      }
    }
  }
}