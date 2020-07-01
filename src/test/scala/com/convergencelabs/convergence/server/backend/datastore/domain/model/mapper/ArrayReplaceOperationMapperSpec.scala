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

package com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper

import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ArrayReplaceOperationMapper.{ArrayReplaceOperationToODocument, ODocumentToArrayReplaceOperation}
import com.convergencelabs.convergence.server.domain.model.data.StringValue
import com.convergencelabs.convergence.server.domain.model.ot.AppliedArrayReplaceOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ArrayReplaceOperationMapperSpec
    extends AnyWordSpec
    with Matchers {

  "An ArrayReplaceOperationMapper" when {
    "when converting ArrayReplaceOperation operations" must {
      "correctly map and unmap a ArrayReplaceOperation" in {
        val op = AppliedArrayReplaceOperation("vid", noOp = true, 4, StringValue("arom-test", "test"), Some(StringValue("oldId", "oldValue"))) // scalastyle:ignore magic.number
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
