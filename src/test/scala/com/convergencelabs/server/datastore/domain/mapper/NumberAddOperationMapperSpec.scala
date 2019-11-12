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

import com.convergencelabs.server.domain.model.ot.AppliedNumberAddOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import NumberAddOperationMapper.NumberAddOperationToODocument
import NumberAddOperationMapper.ODocumentToNumberAddOperation

class NumberAddOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An NumberAddOperationMapper" when {
    "when converting NumberAddOperation operations" must {
      "correctly map and unmap a NumberAddOperation" in {
        val op = AppliedNumberAddOperation("vid", true, 4) // scalastyle:ignore magic.number
        val opDoc = op.asODocument
        val reverted = opDoc.asNumberAddOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asNumberAddOperation
        }
      }
    }
  }
}
