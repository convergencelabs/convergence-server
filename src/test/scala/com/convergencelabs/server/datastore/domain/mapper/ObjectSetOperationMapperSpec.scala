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
import com.convergencelabs.server.domain.model.ot.AppliedObjectSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ObjectSetOperationMapper.ODocumentToObjectSetOperation
import ObjectSetOperationMapper.ObjectSetOperationToODocument

class ObjectSetOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An ObjectSetOperationMapper" when {
    "when converting ObjectSetOperation operations" must {
      "correctly map and unmap a ObjectSetOperation" in {
        val op = AppliedObjectSetOperation("vid", true, Map("foo" -> StringValue("vid2", "test")), Some(Map("old" -> StringValue("oldId", "oldValue"))))
        val opDoc = op.asODocument
        val reverted = opDoc.asObjectSetOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asObjectSetOperation
        }
      }
    }
  }
}
