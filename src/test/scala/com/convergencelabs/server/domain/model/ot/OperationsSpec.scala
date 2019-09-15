package com.convergencelabs.server.domain.model.ot

import scala.math.BigInt.int2bigInt
import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JInt
import org.json4s.JsonAST.JObject
import org.scalatest.FunSuite
import org.scalatest.Matchers
import org.json4s.JsonAST.JDouble
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.data.NullValue
import java.time.Instant

class OperationsSpec extends FunSuite with Matchers {

  val valueId = "vid"

  val nv = NullValue(valueId)

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
    val original = ArrayInsertOperation(valueId, false, 1, nv)
    original.clone(true) shouldBe ArrayInsertOperation(valueId, true, 1, nv)
  }

  test("ArrayRemoveOperation must preserve other fields when setting noOp and path") {
    val original = ArrayRemoveOperation(valueId, false, 1)
    original.clone(true) shouldBe ArrayRemoveOperation(valueId, true, 1)
  }

  test("ArrayReplaceOperation must preserve other fields when setting noOp and path") {
    val original = ArrayReplaceOperation(valueId, false, 1, nv)
    original.clone(true) shouldBe ArrayReplaceOperation(valueId, true, 1, nv)
  }

  test("ArraySetOperation must preserve other fields when setting noOp and path") {
    val original = ArraySetOperation(valueId, false, List(nv))
    original.clone(true) shouldBe ArraySetOperation(valueId, true, List(nv))
  }

  test("ArrayMoveOperation must preserve other fields when setting noOp and path") {
    val original = ArrayMoveOperation(valueId, false, 1, 2)
    original.clone(true) shouldBe ArrayMoveOperation(valueId, true, 1, 2)
  }

  // Object Operations

  test("ObjectSetPropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectSetPropertyOperation(valueId, false, "setProp", nv)
    original.clone(true) shouldBe ObjectSetPropertyOperation(valueId, true, "setProp", nv)
  }

  test("ObjectAddPropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectAddPropertyOperation(valueId, false, "addProp", nv)
    original.clone(true) shouldBe ObjectAddPropertyOperation(valueId, true, "addProp", nv)
  }

  test("ObjectRemovePropertyOperation must preserve other fields when setting noOp and path") {
    val original = ObjectRemovePropertyOperation(valueId, false, "removeProp")
    original.clone(true) shouldBe ObjectRemovePropertyOperation(valueId, true, "removeProp")
  }

  test("ObjectSetOperation must preserve other fields when setting noOp and path") {
    val original = ObjectSetOperation(valueId, false, Map())
    original.clone(true) shouldBe ObjectSetOperation(valueId, true, Map())
  }

  // Number Operations

  test("NumberSetOperation must preserve other fields when setting noOp and path") {
    val original = NumberSetOperation(valueId, false, 1)
    original.clone(true) shouldBe NumberSetOperation(valueId, true, 1)
  }

  test("NumberAddOperation must preserve other fields when setting noOp and path") {
    val original = NumberAddOperation(valueId, false, 1)
    original.clone(true) shouldBe NumberAddOperation(valueId, true, 1)
  }

  // Boolean Operations

  test("BooleanSetOperation must preserve other fields when setting noOp and path") {
    val original = BooleanSetOperation(valueId, false, true)
    original.clone(true) shouldBe BooleanSetOperation(valueId, true, true)
  }
  
  // Date Operations

  test("DateSetOperation must preserve other fields when setting noOp and path") {
    val now = Instant.now()
    val original = DateSetOperation(valueId, false, now)
    original.clone(true) shouldBe DateSetOperation(valueId, true, now)
  }
}
