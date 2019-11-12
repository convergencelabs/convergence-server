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

package com.convergencelabs.convergence.server.db.schema

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.TryValues._

class DeltaManagerSpec extends WordSpecLike with Matchers {

  "DeltaManager" when {

    "missing a released delta" must {
      "not validate" in {
        val manager = new DeltaManager(Some("/schema/missingReleasedDelta/"))
        val manifest = manager.manifest(DeltaCategory.Convergence).success.value
        manifest.validateIndex().failure
      }
    }

    "missing a non released delta" must {
      "not validate" in {
        val manager = new DeltaManager(Some("/schema/missingNonReleasedDelta/"))
        val manifest = manager.manifest(DeltaCategory.Convergence).success.value
        manifest.validateIndex().failure
      }
    }

    "having a bad hash" must {
      "not validate" in {
        // TODO: Implement this test once hashing is completed
//        val manager = new DeltaManager(Some("/schema/badHashDelta/"))
//        val manifest = manager.manifest(DeltaCategory.Convergence).get
//        manifest.validateIndex().failure
      }
    }

    "processing good deltas" must {
      "validate the index" in {
        val manager = new DeltaManager(Some("/schema/goodDeltas/"))
        val manifest = manager.manifest(DeltaCategory.Convergence).get
        manifest.validateIndex().get
      }

      "return the correct maxDelta" in {
        val manager = new DeltaManager(Some("/schema/goodDeltas/"))
        val manifest = manager.manifest(DeltaCategory.Convergence).get
        manifest.maxPreReleaseVersion() shouldBe 3
      }

      "return the correct maxReleasedDelta" in {
        val manager = new DeltaManager(Some("/schema/goodDeltas/"))
        val manifest = manager.manifest(DeltaCategory.Convergence).get
        manifest.maxReleasedVersion() shouldBe 2
      }

      "return the correct incremental delta" in {
        val manager = new DeltaManager(Some("/schema/goodDeltas/"))
        val manifest = manager.manifest(DeltaCategory.Convergence).get
        val delta = manifest.getIncrementalDelta(1).get

        delta.delta.version shouldBe 1
        delta.delta.description shouldBe Some("Initial schema creation")
        delta.delta.actions shouldBe List(
          CreateClass("TestClass1", None, None, List(
            Property("prop1", OrientType.String, None, None, Some(Constraints(None, None, Some(true), None, Some(true), None, None, None, None))),
            Property("prop2", OrientType.Link, None, Some("TestClass2"), Some(Constraints(None, None, Some(true), None, Some(true), None, None, None, None))))),
          CreateClass("TestClass2", None, None, List(
            Property("prop3", OrientType.String, None, None, Some(Constraints(None, None, Some(true), None, Some(true), None, None, None, None))))),
          CreateIndex("TestClass1", "TestClass1.prop1", IndexType.Unique, List("prop1"), None),
          CreateSequence("TestSeq1", SequenceType.Ordered, None, None, None))
      }
    }
  }
}