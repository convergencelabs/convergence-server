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

import com.convergencelabs.server.domain.model.ot.AppliedStringSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import StringSetOperationMapper.ODocumentToStringSetOperation
import StringSetOperationMapper.StringSetOperationToODocument

class StringSetOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An StringSetOperationMapper" when {
    "when converting StringSetOperation operations" must {
      "correctly map and unmap a StringSetOperation" in {
        val op = AppliedStringSetOperation("vid", true, "test", Some("oldValue"))
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
