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

import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.ObjectSetOperationMapper.{ODocumentToObjectSetOperation, ObjectSetOperationToODocument}
import com.convergencelabs.convergence.server.domain.model.data.StringValue
import com.convergencelabs.convergence.server.domain.model.ot.AppliedObjectSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ObjectSetOperationMapperSpec
    extends AnyWordSpec
    with Matchers {

  "An ObjectSetOperationMapper" when {
    "when converting ObjectSetOperation operations" must {
      "correctly map and unmap a ObjectSetOperation" in {
        val op = AppliedObjectSetOperation("vid", noOp = true, Map("foo" -> StringValue("vid2", "test")), Some(Map("old" -> StringValue("oldId", "oldValue"))))
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
