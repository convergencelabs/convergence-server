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

package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import java.time.Instant

import com.convergencelabs.convergence.server.model.domain.model.NullValue
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class OperationsSpec extends AnyFunSuite with Matchers {

  private val valueId = "vid"

  private val nv = NullValue(valueId)

  // String Operations

  test("StringInsertOperation must preserve other fields when setting noOp and path") {
    val original = StringInsertOperation(valueId, noOp = false, 1, "insert")
    original.clone(true) shouldBe StringInsertOperation(valueId, noOp = true, 1, "insert")
  }

  test("StringRemoveOperation must preserve other fields when setting noOp and path") {
    val original = StringRemoveOperation(valueId, noOp = false, 1, "remove")
    original.clone(true) shouldBe StringRemoveOperation(valueId, noOp = true, 1, "remove")
  }

  test("StringSetOperation must preserve other fields when setting noOp and path") {
    val original = StringSetOperation(valueId, noOp = false, "set")
    original.clone(true) shouldBe StringSetOperation(valueId, noOp = true, "set")
  }

  // Array Operations

  test("ArrayInsertOperation must preserve other fields when setting noOp and path") {
    val original = ArrayInsertOperation(valueId, noOp = false, 1, nv)
    original.clone(true) shouldBe ArrayInsertOperation(valueId, noOp = true, 1, nv)
  }

  test("ArrayRemoveOperation must preserve other fields when setting noOp and path") {
    val original = ArrayRemoveOperation(valueId, noOp = false, 1)
    original.clone(true) shouldBe ArrayRemoveOperation(valueId, noOp = true, 1)
  }

  test("ArrayReplaceOperation must preserve other fields when setting noOp and path") {
    val original = ArrayReplaceOperation(valueId, noOp = false, 1, nv)
    original.clone(true) shouldBe ArrayReplaceOperation(valueId, noOp = true, 1, nv)
  }

  test("ArraySetOperation must preserve other fields when setting noOp and path") {
    val original = ArraySetOperation(valueId, noOp = false, List(nv))
    original.clone(true) shouldBe ArraySetOperation(valueId, noOp = true, List(nv))
  }

  test("ArrayMoveOperation must preserve other fields when setting noOp and path") {
    val original = ArrayMoveOperation(valueId, noOp = false, 1, 2)
    original.clone(true) shouldBe ArrayMoveOperation(valueId, noOp = true, 1, 2)
  }

  // Object Operations

  test("ObjectSetPropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectSetPropertyOperation(valueId, noOp = false, "setProp", nv)
    original.clone(true) shouldBe ObjectSetPropertyOperation(valueId, noOp = true, "setProp", nv)
  }

  test("ObjectAddPropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectAddPropertyOperation(valueId, noOp = false, "addProp", nv)
    original.clone(true) shouldBe ObjectAddPropertyOperation(valueId, noOp = true, "addProp", nv)
  }

  test("ObjectRemovePropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectRemovePropertyOperation(valueId, noOp = false, "removeProp")
    original.clone(true) shouldBe ObjectRemovePropertyOperation(valueId, noOp = true, "removeProp")
  }

  test("ObjectSetOperation must preserve other fields when setting noOp and path") {
    val original = ObjectSetOperation(valueId, noOp = false, Map())
    original.clone(true) shouldBe ObjectSetOperation(valueId, noOp = true, Map())
  }

  // Number Operations

  test("NumberSetOperation must preserve other fields when setting noOp and path") {
    val original = NumberSetOperation(valueId, noOp = false, 1)
    original.clone(true) shouldBe NumberSetOperation(valueId, noOp = true, 1)
  }

  test("NumberAddOperation must preserve other fields when setting noOp and path") {
    val original = NumberAddOperation(valueId, noOp = false, 1)
    original.clone(true) shouldBe NumberAddOperation(valueId, noOp = true, 1)
  }

  // Boolean Operations

  test("BooleanSetOperation must preserve other fields when setting noOp and path") {
    val original = BooleanSetOperation(valueId, noOp = false, value = true)
    original.clone(true) shouldBe BooleanSetOperation(valueId, noOp = true, value = true)
  }
  
  // Date Operations

  test("DateSetOperation must preserve other fields when setting noOp and path") {
    val now = Instant.now()
    val original = DateSetOperation(valueId, noOp = false, now)
    original.clone(true) shouldBe DateSetOperation(valueId, noOp = true, now)
  }
}
