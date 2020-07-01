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

package com.convergencelabs.convergence.server.backend.services.domain.model.value

import com.convergencelabs.convergence.server.backend.services.domain.model.ot.ArraySetOperation
import com.convergencelabs.convergence.server.model.domain.model.{ArrayValue, BooleanValue, DoubleValue, StringValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar

class RealTimeArraySpec extends AnyWordSpec with Matchers with MockitoSugar {

  "A RealTimeObject" when {
    "constructed" must {
      "contain the correct values" in new TestFixture {

        val rta = new RealtimeArray(arrayValue, None, None, valueFactory)

        rta.id shouldBe objectId

        rta.parent shouldBe None
        rta.parentField shouldBe None
        rta.children.size shouldBe 3

        val c1 = rta.child(child1Index).get.get
        c1 shouldBe a[RealtimeString]
        c1.id shouldBe child1Id
        c1.data() shouldBe child1Value

        val c2 = rta.child(child2Index).get.get
        c2 shouldBe a[RealtimeBoolean]
        c2.id shouldBe child2Id
        c2.data() shouldBe child2Value

        val c3 = rta.child(child3Index).get.get
        c3 shouldBe a[RealtimeDouble]
        c3.id shouldBe child3Id
        c3.data() shouldBe child3Value
      }
    }

    "asked for its data" must {
      "return correct primitive values" in new TestFixture {
        val rta = new RealtimeArray(arrayValue, None, None, valueFactory)
        val data = rta.data()
        data.size shouldBe 3

        data shouldBe List(child1Value, child2Value, child3Value)
      }
    }
    
    "processing an set value operation" must {
      "detach all children" in new TestFixture {
        val rta = new RealtimeArray(arrayValue, None, None, valueFactory)
        
        val c1 = rta.child(child1Index).get.get
        var c1Detached = false
        c1.addDetachListener(_ => c1Detached = true)
        
        val c2 = rta.child(child1Index).get.get
        var c2Detached = false
        c2.addDetachListener(_ => c2Detached = true)
        
        val c3 = rta.child(child3Index).get.get
        var c3Detached = false
        c1.addDetachListener(_ => c3Detached = true)
        
        rta.processOperation(new ArraySetOperation(objectId, false, List()))
        
        c1Detached shouldBe true
        c2Detached shouldBe true
        c3Detached shouldBe true
      }
    }
    
    // TODO we need to test the other operations
  }

  trait TestFixture {
    val objectId = "0"

    val child1Index = 0
    val child1Id = "1"
    val child1Value = "str 1"

    val child2Index = 1
    val child2Id = "2"
    val child2Value = true

    val child3Index = 2
    val child3Id = "3"
    val child3Value = 4

    val arrayValue: ArrayValue = ArrayValue(objectId, List(
      StringValue(child1Id, child1Value),
      BooleanValue(child2Id, child2Value),
      DoubleValue(child3Id, child3Value)))

    val valueFactory: RealtimeValueFactory = new RealtimeValueFactory() {}
  }
}
