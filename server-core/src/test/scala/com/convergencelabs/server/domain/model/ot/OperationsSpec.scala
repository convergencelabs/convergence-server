package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JObject
import org.scalatest.FunSuite
import org.scalatest.Matchers

class OperationsSpec extends FunSuite with Matchers {

  // String Operations

  test("StringInsertOperation must preserve other fields when setting noOp and path") {
    val original = StringInsertOperation(List(), false, 1, "insert")
    original.clone(List(2), true) shouldBe StringInsertOperation(List(2), true, 1, "insert")
  }

  test("StringRemoveOperation must preserve other fields when setting noOp and path") {
    val original = StringRemoveOperation(List(), false, 1, "remove")
    original.clone(List(2), true) shouldBe StringRemoveOperation(List(2), true, 1, "remove")
  }

  test("StringSetOperation must preserve other fields when setting noOp and path") {
    val original = StringSetOperation(List(), false, "set")
    original.clone(List(2), true) shouldBe StringSetOperation(List(2), true, "set")
  }

  // Array Operations

  test("ArrayInsertOperation must preserve other fields when setting noOp and path") {
    val original = ArrayInsertOperation(List(), false, 1, JInt(1))
    original.clone(List(2), true) shouldBe ArrayInsertOperation(List(2), true, 1, JInt(1))
  }

  test("ArrayRemoveOperation must preserve other fields when setting noOp and path") {
    val original = ArrayRemoveOperation(List(), false, 1)
    original.clone(List(2), true) shouldBe ArrayRemoveOperation(List(2), true, 1)
  }

  test("ArrayReplaceOperation must preserve other fields when setting noOp and path") {
    val original = ArrayReplaceOperation(List(), false, 1, JInt(1))
    original.clone(List(2), true) shouldBe ArrayReplaceOperation(List(2), true, 1, JInt(1))
  }

  test("ArraySetOperation must preserve other fields when setting noOp and path") {
    val original = ArraySetOperation(List(), false, JArray(List(JInt(1))))
    original.clone(List(2), true) shouldBe ArraySetOperation(List(2), true, JArray(List(JInt(1))))
  }

  test("ArrayMoveOperation must preserve other fields when setting noOp and path") {
    val original = ArrayMoveOperation(List(), false, 1, 2)
    original.clone(List(2), true) shouldBe ArrayMoveOperation(List(2), true, 1, 2)
  }

  // Object Operations

  test("ObjectSetPropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectSetPropertyOperation(List(), false, "setProp", JInt(1))
    original.clone(List(2), true) shouldBe ObjectSetPropertyOperation(List(2), true, "setProp", JInt(1))
  }

  test("ObjectAddPropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectAddPropertyOperation(List(), false, "addProp", JInt(1))
    original.clone(List(2), true) shouldBe ObjectAddPropertyOperation(List(2), true, "addProp", JInt(1))
  }

  test("ObjectRemovePropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectRemovePropertyOperation(List(), false, "removeProp")
    original.clone(List(2), true) shouldBe ObjectRemovePropertyOperation(List(2), true, "removeProp")
  }

  test("ObjectSetOperation must preserve other fields when setting noOp and path") {
    val original = ObjectSetOperation(List(), false, JObject())
    original.clone(List(2), true) shouldBe ObjectSetOperation(List(2), true, JObject())
  }

  // Number Operations

  test("NumberSetOperation must preserve other fields when setting noOp and path") {
    val original = NumberSetOperation(List(), false, JInt(1))
    original.clone(List(2), true) shouldBe NumberSetOperation(List(2), true, JInt(1))
  }

  test("NumberAddOperation must preserve other fields when setting noOp and path") {
    val original = NumberAddOperation(List(), false, JInt(1))
    original.clone(List(2), true) shouldBe NumberAddOperation(List(2), true, JInt(1))
  }

  // Boolean Operations

  test("BooleanSetOperation must preserve other fields when setting noOp and path") {
    val original = BooleanSetOperation(List(), false, true)
    original.clone(List(2), true) shouldBe BooleanSetOperation(List(2), true, true)
  }
}
