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

package com.convergencelabs.convergence.server.backend.services.domain.model

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.ObjectSetOperation
import com.convergencelabs.convergence.server.backend.services.domain.model.value._
import com.convergencelabs.convergence.server.model.domain.model.{BooleanValue, DoubleValue, ObjectValue, StringValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

// scalastyle:off magic.number
class RealTimeObjectSpec
  extends AnyWordSpec
  with Matchers
  with MockitoSugar {

  "A RealTimeObject" when {
    "constructed" must {
      "contain the correct values" in new TestFixture {

        val rto = new RealtimeObject(objectValue, None, None, valueFactory)

        rto.id shouldBe objectId

        rto.parent shouldBe None
        rto.parentField shouldBe None
        rto.children.size shouldBe 3

        val c1 = rto.child(child1Key).get.get
        c1 shouldBe a[RealtimeString]
        c1.id shouldBe child1Id
        c1.data() shouldBe child1Value

        val c2 = rto.child(child2Key).get.get
        c2 shouldBe a[RealtimeBoolean]
        c2.id shouldBe child2Id
        c2.data() shouldBe child2Value

        val c3 = rto.child(child3Key).get.get
        c3 shouldBe a[RealtimeDouble]
        c3.id shouldBe child3Id
        c3.data() shouldBe child3Value

      }
    }

    "asked for its data" must {
      "return correct primitive values" in new TestFixture {
        val rto = new RealtimeObject(objectValue, None, None, valueFactory)
        val data = rto.data()
        data.size shouldBe 3
        data.keySet shouldBe Set(child1Key, child2Key, child3Key)

        data shouldBe Map(
          (child1Key -> child1Value),
          (child2Key -> child2Value),
          (child3Key -> child3Value))
      }
    }

    "processing an set value operation" must {
      "detach all children" in new TestFixture {
        val rto = new RealtimeObject(objectValue, None, None, valueFactory)

        val c1 = rto.child(child1Key).get.get
        var c1Detached = false
        c1.addDetachListener(_ => c1Detached = true)

        val c2 = rto.child(child1Key).get.get
        var c2Detached = false
        c2.addDetachListener(_ => c2Detached = true)

        val c3 = rto.child(child3Key).get.get
        var c3Detached = false
        c1.addDetachListener(_ => c3Detached = true)

        rto.processOperation(new ObjectSetOperation(objectId, false, Map()))

        c1Detached shouldBe true
        c2Detached shouldBe true
        c3Detached shouldBe true
      }
    }

    // TODO we need to test the other operations
  }

  trait TestFixture {
    val objectId = "0"

    val child1Key = "child1"
    val child1Id = "1"
    val child1Value = "str 1"

    val child2Key = "child2"
    val child2Id = "2"
    val child2Value = true

    val child3Key = "child3"
    val child3Id = "3"
    val child3Value = 4

    val objectValue = new ObjectValue(objectId, Map(
      (child1Key -> StringValue(child1Id, child1Value)),
      (child2Key -> BooleanValue(child2Id, child2Value)),
      (child3Key -> DoubleValue(child3Id, child3Value))))

    val valueFactory = new RealtimeValueFactory() {}
  }
}
