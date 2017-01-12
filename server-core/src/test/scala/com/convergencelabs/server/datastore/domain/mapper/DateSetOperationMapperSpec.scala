package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.orientechnologies.orient.core.record.impl.ODocument

import DateSetOperationMapper.DateSetOperationToODocument
import DateSetOperationMapper.ODocumentToDateSetOperation
import java.time.Instant
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation

class DateSetOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An DateSetOperationMapper" when {
    "when converting DateSetOperation operations" must {
      "correctly map and unmap a DateSetOperation" in {
        val date = Instant.now()
        val op = AppliedDateSetOperation("vid", true, date, Some(date))
        val opDoc = op.asODocument
        val reverted = opDoc.asDateSetOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asDateSetOperation
        }
      }
    }
  }
}
