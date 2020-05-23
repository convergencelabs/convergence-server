/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.datastore.domain.mapper

import org.scalatest.Finders
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.convergencelabs.convergence.server.domain.model.data.StringValue
import com.convergencelabs.convergence.server.domain.model.ot.AppliedArrayRemoveOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ArrayRemoveOperationMapper.ArrayRemoveOperationToODocument
import ArrayRemoveOperationMapper.ODocumentToArrayRemoveOperation

class ArrayRemoveOperationMapperSpec
    extends AnyWordSpec
    with Matchers {


  "An ArrayRemoveOperationMapper" when {
    "when converting ArrayRemoveOperation operations" must {
      "correctly map and unmap a ArrayRemoveOperation" in {
        val op = AppliedArrayRemoveOperation("vid", true, 4, Some(StringValue("oldId", "oldValue"))) // scalastyle:ignore magic.number
        val opDoc = op.asODocument
        val reverted = opDoc.asArrayRemoveOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asArrayRemoveOperation
        }
      }
    }
  }
}
