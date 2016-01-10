package com.convergencelabs.server.domain.model.ot

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant

import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.json4s.DefaultFormats
import org.json4s.JArray
import org.json4s.JBool
import org.json4s.JInt
import org.json4s.JString
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JObject
import org.json4s.jackson.Serialization.read
import org.scalatest.Finders
import org.scalatest.FunSpec

class OTFTestHarness extends FunSpec {

  val commonPath = List()

  val registry = new TransformationFunctionRegistry()
  implicit val format = DefaultFormats

  for (file <- new File("src/test/otfspec").listFiles.filter { x => x.getName.endsWith("spec.json") }) {
    processFile(file)
  }

  def processFile(specFile: File): Unit = {
    val contents = new String(Files.readAllBytes(Paths.get(specFile.getAbsolutePath)))
    val spec = read[OTFSpec](contents)

    describe(s"Testing transformation of a server ${spec.serverOpType} and a client ${spec.clientOpType}") {
      for (testCase <- spec.cases) {
        val JString(testCaseServerType) = testCase.input.serverOp \\ "type"
        val JString(testCaseClientType) = testCase.input.clientOp \\ "type"

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

    it(s"Testing transformation of ${originalServerOp} and a client ${originalClientOp}") {
      val tf = registry.getTransformationFunction(originalServerOp, originalClientOp).get

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

  implicit def jObject2Operation(obj: JObject): DiscreteOperation = {
    obj match {
      case JObject(List(("type", JString("StringInsert")), ("noOp", JBool(noOp)), ("index", JInt(index)), ("value", JString(value)))) =>
        StringInsertOperation(commonPath, noOp, index.intValue(), value)
      case JObject(List(("type", JString("StringRemove")), ("noOp", JBool(noOp)), ("index", JInt(index)), ("value", JString(value)))) =>
        StringRemoveOperation(commonPath, noOp, index.intValue(), value)
      case JObject(List(("type", JString("StringSet")), ("noOp", JBool(noOp)), ("value", JString(value)))) =>
        StringSetOperation(commonPath, noOp, value)

      case JObject(List(("type", JString("ArrayInsert")), ("noOp", JBool(noOp)), ("index", JInt(index)), ("value", value))) =>
        ArrayInsertOperation(commonPath, noOp, index.intValue(), value)
      case JObject(List(("type", JString("ArrayRemove")), ("noOp", JBool(noOp)), ("index", JInt(index)))) =>
        ArrayRemoveOperation(commonPath, noOp, index.intValue())
      case JObject(List(("type", JString("ArrayReplace")), ("noOp", JBool(noOp)), ("index", JInt(index)), ("value", value))) =>
        ArrayReplaceOperation(commonPath, noOp, index.intValue(), value)
      case JObject(List(("type", JString("ArrayMove")), ("noOp", JBool(noOp)), ("fromIndex", JInt(fromIndex)), ("toIndex", JInt(toIndex)))) =>
        ArrayMoveOperation(commonPath, noOp, fromIndex.intValue(), toIndex.intValue())
      case JObject(List(("type", JString("ArraySet")), ("noOp", JBool(noOp)), ("value", value @ JArray(_)))) =>
        ArraySetOperation(commonPath, noOp, value)

      case JObject(List(("type", JString("ObjectAddProperty")), ("noOp", JBool(noOp)), ("prop", JString(prop)), ("value", value))) =>
        ObjectAddPropertyOperation(commonPath, noOp, prop, value)
      case JObject(List(("type", JString("ObjectSetProperty")), ("noOp", JBool(noOp)), ("prop", JString(prop)), ("value", value))) =>
        ObjectSetPropertyOperation(commonPath, noOp, prop, value)
      case JObject(List(("type", JString("ObjectRemoveProperty")), ("noOp", JBool(noOp)), ("prop", JString(prop)))) =>
        ObjectRemovePropertyOperation(commonPath, noOp, prop)
      case JObject(List(("type", JString("ObjectSet")), ("noOp", JBool(noOp)), ("value", value @ JObject(_)))) =>
        ObjectSetOperation(commonPath, noOp, value)

      case JObject(List(("type", JString("BooleanSet")), ("noOp", JBool(noOp)), ("value", JBool(value)))) =>
        BooleanSetOperation(commonPath, noOp, value)

      case JObject(List(("type", JString("NumberAdd")), ("noOp", JBool(noOp)), ("value", value))) =>
        NumberAddOperation(commonPath, noOp, value.asInstanceOf[JNumber])

      case JObject(List(("type", JString("NumberSet")), ("noOp", JBool(noOp)), ("value", value))) =>
        NumberSetOperation(commonPath, noOp, value.asInstanceOf[JNumber])

      case _ =>
        throw new IllegalArgumentException(s"Invalid operation definition: $obj")
    }
  }
}