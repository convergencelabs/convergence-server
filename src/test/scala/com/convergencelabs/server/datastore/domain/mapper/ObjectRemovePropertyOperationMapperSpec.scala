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
import com.convergencelabs.server.domain.model.ot.AppliedObjectRemovePropertyOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ObjectRemovePropertyOperationMapper.ODocumentToObjectRemovePropertyOperation
import ObjectRemovePropertyOperationMapper.ObjectRemovePropertyOperationToODocument

class ObjectRemovePropertyOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An ObjectRemovePropertyOperationMapper" when {
    "when converting ObjectRemovePropertyOperation operations" must {
      "correctly map and unmap a ObjectRemovePropertyOperation" in {
        val op = AppliedObjectRemovePropertyOperation("vid", true, "foo", Some(StringValue("oldId", "oldValue")))
        val opDoc = op.asODocument
        val reverted = opDoc.asObjectRemovePropertyOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asObjectRemovePropertyOperation
        }
      }
    }
  }
}
