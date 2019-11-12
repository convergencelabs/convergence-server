/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

import java.io.File
import java.nio.file.{Files, Paths}
import java.time.Instant

import com.convergencelabs.server.domain.model.data._
import com.convergencelabs.server.domain.model.ot.OTFTestHarnessSpec._
import org.json4s.JsonAST._
import org.json4s.jackson.Serialization.read
import org.json4s.{DefaultFormats, JArray, JBool, JInt, JString, jvalue2monadic}
import org.scalatest.FunSpec

import scala.language.implicitConversions

object OTFTestHarnessSpec {
  val Type = "type"
  val NoOp = "noOp"
  val Value = "value"
  val Index = "index"
  val Prop = "prop"
}

class OTFTestHarnessSpec extends FunSpec {

  val valueId = "vid"

  val registry = new TransformationFunctionRegistry()
  implicit val format = DefaultFormats

  for {
    file <- new File("src/test/otfspec").listFiles.filter { x => x.getName.endsWith("-spec.json") }
  } {
    processFile(file)
  }

  def processFile(specFile: File): Unit = {
    val contents = new String(Files.readAllBytes(Paths.get(specFile.getAbsolutePath)))
    val spec = read[OTFSpec](contents)

    describe(s"Testing transformation of a server ${spec.serverOpType} and a client ${spec.clientOpType}") {
      for {
        testCase <- spec.cases
      } {
        val JString(testCaseServerType) = testCase.input.serverOp \\ Type
        val JString(testCaseClientType) = testCase.input.clientOp \\ Type

        if (spec.serverOpType != testCaseServerType || spec.clientOpType != testCaseClientType) {
          throw new IllegalArgumentException(
            s"The spec file contains an invalid test case, because the input operations are of the wrong type:\n${specFile.getAbsolutePath}")
        }

        processCase(testCase)
      }
    }
  }

  def processCase(testCase: OTFTestCase): Unit = {
    val originalServerOp: DiscreteOperation = testCase.input.serverOp
    val originalClientOp: DiscreteOperation = testCase.input.clientOp

    it(s"Testing transformation of $originalServerOp and a client $originalClientOp") {
      val tf = registry.getOperationTransformationFunction(originalServerOp, originalClientOp).get

      testCase.error match {
        case Some(true) =>
          intercept[IllegalArgumentException] {
            tf.transform(originalServerOp, originalClientOp)
          }
        case _ =>
          val (sPrime, cPrime) = tf.transform(originalServerOp, originalClientOp)
          val expectedServerOp: DiscreteOperation = testCase.output.get.serverOp
          val expectedClientOp: DiscreteOperation = testCase.output.get.clientOp

          assert(sPrime == expectedServerOp, "server operation was transformed incorrectly")
          assert(cPrime == expectedClientOp, "server operation was transformed incorrectly")
      }
    }
  }

  // scalastyle:off cyclomatic.complexity
  implicit def jObject2Operation(obj: JObject): DiscreteOperation = {
    obj match {
      case JObject(List((Type, JString("StringInsert")), (NoOp, JBool(noOp)), (Index, JInt(index)), (Value, JString(value)))) =>
        StringInsertOperation(valueId, noOp, index.intValue(), value)
      case JObject(List((Type, JString("StringRemove")), (NoOp, JBool(noOp)), (Index, JInt(index)), (Value, JString(value)))) =>
        StringRemoveOperation(valueId, noOp, index.intValue(), value)
      case JObject(List((Type, JString("StringSet")), (NoOp, JBool(noOp)), (Value, JString(value)))) =>
        StringSetOperation(valueId, noOp, value)

      case JObject(List((Type, JString("ArrayInsert")), (NoOp, JBool(noOp)), (Index, JInt(index)), (Value, value))) =>
        ArrayInsertOperation(valueId, noOp, index.intValue(), mapToDataValue(value))
      case JObject(List((Type, JString("ArrayRemove")), (NoOp, JBool(noOp)), (Index, JInt(index)))) =>
        ArrayRemoveOperation(valueId, noOp, index.intValue())
      case JObject(List((Type, JString("ArrayReplace")), (NoOp, JBool(noOp)), (Index, JInt(index)), (Value, value))) =>
        ArrayReplaceOperation(valueId, noOp, index.intValue(), mapToDataValue(value))
      case JObject(List((Type, JString("ArrayMove")), (NoOp, JBool(noOp)), ("fromIndex", JInt(fromIndex)), ("toIndex", JInt(toIndex)))) =>
        ArrayMoveOperation(valueId, noOp, fromIndex.intValue(), toIndex.intValue())
      case JObject(List((Type, JString("ArraySet")), (NoOp, JBool(noOp)), (Value, value @ JArray(values)))) =>
        ArraySetOperation(valueId, noOp, values.map { v => mapToDataValue(v) })

      case JObject(List((Type, JString("ObjectAddProperty")), (NoOp, JBool(noOp)), (Prop, JString(prop)), (Value, value))) =>
        ObjectAddPropertyOperation(valueId, noOp, prop, mapToDataValue(value))
      case JObject(List((Type, JString("ObjectSetProperty")), (NoOp, JBool(noOp)), (Prop, JString(prop)), (Value, value))) =>
        ObjectSetPropertyOperation(valueId, noOp, prop, mapToDataValue(value))
      case JObject(List((Type, JString("ObjectRemoveProperty")), (NoOp, JBool(noOp)), (Prop, JString(prop)))) =>
        ObjectRemovePropertyOperation(valueId, noOp, prop)
      case JObject(List((Type, JString("ObjectSet")), (NoOp, JBool(noOp)), (Value, value @ JObject(fields)))) =>
        ObjectSetOperation(valueId, noOp, fields.toMap.mapValues { x => mapToDataValue(x) })

      case JObject(List((Type, JString("BooleanSet")), (NoOp, JBool(noOp)), (Value, JBool(value)))) =>
        BooleanSetOperation(valueId, noOp, value)
        
      case JObject(List((Type, JString("DateSet")), (NoOp, JBool(noOp)), (Value, value))) =>
        DateSetOperation(valueId, noOp, Instant.ofEpochMilli(value.values.toString().toLong))

      // FIXME this is a bit of a hack due to number types.
      case JObject(List((Type, JString("NumberAdd")), (NoOp, JBool(noOp)), (Value, value))) =>
        NumberAddOperation(valueId, noOp, value.values.toString().toDouble)

      case JObject(List((Type, JString("NumberSet")), (NoOp, JBool(noOp)), (Value, value))) =>
        NumberSetOperation(valueId, noOp, value.values.toString().toDouble)

      case _ =>
        throw new IllegalArgumentException(s"Invalid operation definition: $obj")
    }
  }
  // scalastyle:on cyclomatic.complexity

  // scalastyle:off cyclomatic.complexity
  def mapToDataValue(jValue: JValue): DataValue = {
    jValue match {
      case JString(value) => StringValue(valueId, value)
      case JInt(value) => DoubleValue(valueId, value.toDouble)
      case JLong(value) => DoubleValue(valueId, value.toDouble)
      case JDouble(value) => DoubleValue(valueId, value.toDouble)
      case JDecimal(value) => DoubleValue(valueId, value.toDouble)
      case JBool(value) => BooleanValue(valueId, value)
      case JNull => NullValue(valueId)
      case JNothing => NullValue(valueId)
      case JArray(arr) => ArrayValue(valueId, arr.map { v => mapToDataValue(v) })
      case JSet(set) => ArrayValue(valueId, set.toList.map { v => mapToDataValue(v) })
      case JObject(fields) => ObjectValue(valueId, fields.toMap.mapValues { v => mapToDataValue(v) })
    }
  }
  // scalastyle:on cyclomatic.complexity
}

case class OperationPair(serverOp: JObject, clientOp: JObject)
case class OTFTestCase(id: String, input: OperationPair, output: Option[OperationPair], error: Option[Boolean])
case class OTFSpec(serverOpType: String, clientOpType: String, cases: List[OTFTestCase])
