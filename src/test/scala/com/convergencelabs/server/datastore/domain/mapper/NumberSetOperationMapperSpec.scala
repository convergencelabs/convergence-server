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

import com.convergencelabs.server.domain.model.ot.AppliedNumberSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import NumberSetOperationMapper.NumberSetOperationToODocument
import NumberSetOperationMapper.ODocumentToNumberSetOperation

class NumberSetOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An NumberSetOperationMapper" when {
    "when converting NumberSetOperation operations" must {
      "correctly map and unmap a NumberSetOperation" in {
        val op = AppliedNumberSetOperation("vid", true, 4, Some(2)) // scalastyle:ignore magic.number
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
