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

package com.convergencelabs.convergence.server.domain.model

import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.scalatest.mockito.MockitoSugar
import org.scalatest.junit.JUnitRunner

import com.convergencelabs.convergence.server.domain.model.data.BooleanValue
import com.convergencelabs.convergence.server.domain.model.data.DoubleValue
import com.convergencelabs.convergence.server.domain.model.data.StringValue
import com.convergencelabs.convergence.server.domain.model.data.ArrayValue
import com.convergencelabs.convergence.server.domain.model.ot.ArraySetOperation

// scalastyle:off magic.number
@RunWith(classOf[JUnitRunner]) 
class RealTimeArraySpec
  extends WordSpec
  with Matchers
  with MockitoSugar {

  "A RealTimeObject" when {
    "constructed" must {
      "contain the correct values" in new TestFixture {

        val rta = new RealTimeArray(arrayValue, None, None, valueFactory)

        rta.id shouldBe objectId

        rta.parent shouldBe None
        rta.parentField shouldBe None
        rta.children.size shouldBe 3

        val c1 = rta.child(child1Index).get.get
        c1 shouldBe a[RealTimeString]
        c1.id shouldBe child1Id
        c1.data() shouldBe child1Value

        val c2 = rta.child(child2Index).get.get
        c2 shouldBe a[RealTimeBoolean]
        c2.id shouldBe child2Id
        c2.data() shouldBe child2Value

        val c3 = rta.child(child3Index).get.get
        c3 shouldBe a[RealTimeDouble]
        c3.id shouldBe child3Id
        c3.data() shouldBe child3Value
      }
    }

    "asked for its data" must {
      "return correct primitive values" in new TestFixture {
        val rta = new RealTimeArray(arrayValue, None, None, valueFactory)
        val data = rta.data()
        data.size shouldBe 3

        data shouldBe List(child1Value, child2Value, child3Value)
      }
    }
    
    "processing an set value operation" must {
      "detach all children" in new TestFixture {
        val rta = new RealTimeArray(arrayValue, None, None, valueFactory)
        
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

    val arrayValue = new ArrayValue(objectId, List(
      StringValue(child1Id, child1Value),
      BooleanValue(child2Id, child2Value),
      DoubleValue(child3Id, child3Value)))

    val valueFactory = new RealTimeValueFactory()
  }
}
