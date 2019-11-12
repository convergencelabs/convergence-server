/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.mapper

import java.time.Instant

import com.convergencelabs.server.datastore.domain.mapper.DateSetOperationMapper.{DateSetOperationToODocument, ODocumentToDateSetOperation}
import com.convergencelabs.server.domain.model.ot.AppliedDateSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import org.scalatest.{Matchers, WordSpec}

class DateSetOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An DateSetOperationMapper" when {
    "when converting DateSetOperation operations" must {
      "correctly map and unmap a DateSetOperation" in {
        val date = java.util.Date.from(Instant.now()).toInstant
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
