package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JObject
import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.json4s.JsonAST.JDouble

class OperationsSpec extends FunSuite with Matchers {

  val valueId = "vid"
  
  // String Operations

  test("StringInsertOperation must preserve other fields when setting noOp and path") {
    val original = StringInsertOperation(valueId, false, 1, "insert")
    original.clone(true) shouldBe StringInsertOperation(valueId, true, 1, "insert")
  }

  test("StringRemoveOperation must preserve other fields when setting noOp and path") {
    val original = StringRemoveOperation(valueId, false, 1, "remove")
    original.clone(true) shouldBe StringRemoveOperation(valueId, true, 1, "remove")
  }

  test("StringSetOperation must preserve other fields when setting noOp and path") {
    val original = StringSetOperation(valueId, false, "set")
    original.clone(true) shouldBe StringSetOperation(valueId, true, "set")
  }

  // Array Operations

  test("ArrayInsertOperation must preserve other fields when setting noOp and path") {
    val original = ArrayInsertOperation(valueId, false, 1, JInt(1))
    original.clone(true) shouldBe ArrayInsertOperation(valueId, true, 1, JInt(1))
  }

  test("ArrayRemoveOperation must preserve other fields when setting noOp and path") {
    val original = ArrayRemoveOperation(valueId, false, 1)
    original.clone(true) shouldBe ArrayRemoveOperation(valueId, true, 1)
  }

  test("ArrayReplaceOperation must preserve other fields when setting noOp and path") {
    val original = ArrayReplaceOperation(valueId, false, 1, JInt(1))
    original.clone(true) shouldBe ArrayReplaceOperation(valueId, true, 1, JInt(1))
  }

  test("ArraySetOperation must preserve other fields when setting noOp and path") {
    val original = ArraySetOperation(valueId, false, JArray(List(JInt(1))))
    original.clone(true) shouldBe ArraySetOperation(valueId, true, JArray(List(JInt(1))))
  }

  test("ArrayMoveOperation must preserve other fields when setting noOp and path") {
    val original = ArrayMoveOperation(valueId, false, 1, 2)
    original.clone(true) shouldBe ArrayMoveOperation(valueId, true, 1, 2)
  }

  // Object Operations

  test("ObjectSetPropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectSetPropertyOperation(valueId, false, "setProp", JInt(1))
    original.clone(true) shouldBe ObjectSetPropertyOperation(valueId, true, "setProp", JInt(1))
  }

  test("ObjectAddPropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectAddPropertyOperation(valueId, false, "addProp", JInt(1))
    original.clone(true) shouldBe ObjectAddPropertyOperation(valueId, true, "addProp", JInt(1))
  }

  test("ObjectRemovePropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectRemovePropertyOperation(valueId, false, "removeProp")
    original.clone(true) shouldBe ObjectRemovePropertyOperation(valueId, true, "removeProp")
  }

  test("ObjectSetOperation must preserve other fields when setting noOp and path") {
    val original = ObjectSetOperation(valueId, false, JObject())
    original.clone(true) shouldBe ObjectSetOperation(valueId, true, JObject())
  }

  // Number Operations

  test("NumberSetOperation must preserve other fields when setting noOp and path") {
    val original = NumberSetOperation(valueId, false, JDouble(1))
    original.clone(true) shouldBe NumberSetOperation(valueId, true, JDouble(1))
  }

  test("NumberAddOperation must preserve other fields when setting noOp and path") {
    val original = NumberAddOperation(valueId, false, JDouble(1))
    original.clone(true) shouldBe NumberAddOperation(valueId, true, JDouble(1))
  }

  // Boolean Operations

  test("BooleanSetOperation must preserve other fields when setting noOp and path") {
    val original = BooleanSetOperation(valueId, false, true)
    original.clone(true) shouldBe BooleanSetOperation(valueId, true, true)
  }
}
