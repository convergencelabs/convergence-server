package com.convergencelabs.server.datastore.domain.mapper

import org.json4s.JsonAST.JObject
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.CompoundOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation
import CompoundOperationMapper.CompoundOperationToODocument
import CompoundOperationMapper.ODocumentToCompoundOperation
import com.orientechnologies.orient.core.record.impl.ODocument

class CompoundOperationMapperSpec
    extends WordSpec
    with Matchers {

  val path = List(3, "foo", 4)

  "An CompoundOperationMapper" when {
    "when converting compound operations" must {
      "correctly map and unmap a CompoundOperation" in {
        val ops = List(
          ObjectSetOperation(path, true, JObject()),
          ArrayRemoveOperation(path, true, 3))

        val op = CompoundOperation(ops)
        val opDoc = op.asODocument
        val reverted = opDoc.asCompoundOperation
        op shouldBe reverted
      }
      
      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asCompoundOperation
        }
      }
    }
  }
}