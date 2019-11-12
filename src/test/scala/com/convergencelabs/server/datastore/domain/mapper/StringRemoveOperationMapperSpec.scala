/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.server.domain.model.ot.AppliedStringRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import StringRemoveOperationMapper.ODocumentToStringRemoveOperation
import StringRemoveOperationMapper.StringRemoveOperationToODocument

class StringRemoveOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An StringRemoveOperationMapper" when {
    "when converting StringRemoveOperation operations" must {
      "correctly map and unmap a StringRemoveOperation" in {
        val op = AppliedStringRemoveOperation("vid", true, 4, 4, Some("test")) // scalastyle:ignore magic.number
        val opDoc = op.asODocument
        val reverted = opDoc.asStringRemoveOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asStringRemoveOperation
        }
      }
    }
  }
}
