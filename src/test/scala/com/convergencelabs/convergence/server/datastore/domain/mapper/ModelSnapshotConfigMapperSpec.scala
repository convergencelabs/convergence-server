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

import java.time.Duration
import java.time.temporal.ChronoUnit

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec

import com.convergencelabs.convergence.server.domain.ModelSnapshotConfig
import com.orientechnologies.orient.core.record.impl.ODocument

import ModelSnapshotConfigMapper.ModelSnapshotConfigToODocument
import ModelSnapshotConfigMapper.ODocumentToModelSnapshotConfig

class ModelSnapshotConfigMapperSpec
    extends WordSpec
    with Matchers {

  "An ModelSnapshotConfigMapper" when {
    "when converting a ModelSnpashotCofig" must {
      "correctly map and unmap a Model" in {
        val modelSnapshotConfig = ModelSnapshotConfig(
          true,
          true,
          true,
          1L,
          2L,
          true,
          true,
          Duration.of(1, ChronoUnit.SECONDS),
          Duration.of(2, ChronoUnit.SECONDS))

        val doc = modelSnapshotConfig.asODocument
        val reverted = doc.asModelSnapshotConfig
        reverted shouldBe modelSnapshotConfig
      }

      "not allow an invalid document class name" in {
        val invalid = new ODocument("SomeClass")
        intercept[IllegalArgumentException] {
          invalid.asModelSnapshotConfig
        }
      }
    }
  }
}
