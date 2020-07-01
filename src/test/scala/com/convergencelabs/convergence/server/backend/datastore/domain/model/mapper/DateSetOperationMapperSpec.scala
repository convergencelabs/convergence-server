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

import java.time.Instant

import com.convergencelabs.convergence.server.backend.datastore.domain.model.mapper.DateSetOperationMapper.{DateSetOperationToODocument, ODocumentToDateSetOperation}
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.AppliedDateSetOperation
import com.orientechnologies.orient.core.record.impl.ODocument
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DateSetOperationMapperSpec
    extends AnyWordSpec
    with Matchers {

  "An DateSetOperationMapper" when {
    "when converting DateSetOperation operations" must {
      "correctly map and unmap a DateSetOperation" in {
        val date = java.util.Date.from(Instant.now()).toInstant
        val op = AppliedDateSetOperation("vid", noOp = true, date, Some(date))
        val opDoc = op.asODocument
        val reverted = opDoc.asDateSetOperation
        op shouldBe reverted
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asDateSetOperation
        }
      }
    }
  }
}
