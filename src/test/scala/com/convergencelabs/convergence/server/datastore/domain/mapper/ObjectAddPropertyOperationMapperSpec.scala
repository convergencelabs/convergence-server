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
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.convergence.server.domain.model.data.StringValue
import com.convergencelabs.convergence.server.domain.model.ot.AppliedObjectAddPropertyOperation
import com.orientechnologies.orient.core.record.impl.ODocument

import ObjectAddPropertyOperationMapper.ODocumentToObjectAddPropertyOperation
import ObjectAddPropertyOperationMapper.ObjectAddPropertyOperationToODocument

class ObjectAddPropertyOperationMapperSpec
    extends WordSpec
    with Matchers {

  "An ObjectAddPropertyOperationMapper" when {
    "when converting ObjectAddPropertyOperation operations" must {
      "correctly map and unmap a ObjectAddPropertyOperation" in {
        val op = AppliedObjectAddPropertyOperation("vid", true, "foo", StringValue("vid1", "bar"))
        val opDoc = op.asODocument
        val reverted = opDoc.asObjectAddPropertyOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asObjectAddPropertyOperation
        }
      }
    }
  }
}
