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

import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedArrayReplaceOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ArrayReplaceOperationMapper.ArrayReplaceOperationToODocument
import ArrayReplaceOperationMapper.ODocumentToArrayReplaceOperation

class ArrayReplaceOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An ArrayReplaceOperationMapper" when {
    "when converting ArrayReplaceOperation operations" must {
      "correctly map and unmap a ArrayReplaceOperation" in {
        val op = AppliedArrayReplaceOperation("vid", true, 4, StringValue("arom-test", "test"), Some(StringValue("oldId", "oldValue"))) // scalastyle:ignore magic.number
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
