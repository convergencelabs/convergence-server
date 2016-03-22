package com.convergencelabs.server.datastore.domain.mapper

import org.json4s.JsonAST.JObject
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import com.convergencelabs.server.domain.model.ot.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.CompoundOperation
import com.convergencelabs.server.domain.model.ot.ObjectSetOperation
import CompoundOperationMapper.CompoundOperationToODocument
import CompoundOperationMapper.ODocumentToCompoundOperation
import com.orientechnologies.orient.core.record.impl.ODocument

class CompoundOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An CompoundOperationMapper" when {
    "when converting compound operations" must {
      "correctly map and unmap a CompoundOperation" in {
        val ops = List(
          ObjectSetOperation("vid1", true, JObject()),
          ArrayRemoveOperation("vid2", true, 3))

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
