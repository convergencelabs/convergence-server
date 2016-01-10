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
import org.json4s.JsonAST.JObject
import org.json4s.jackson.Serialization.read
import org.json4s.JsonAST.JNumber

object OTFTestHarness extends App {

  val commonPath = List()

  val registry = new TransformationFunctionRegistry()
  implicit val format = DefaultFormats

  val startTime = Instant.now()

  var totalTests = 0
  var passedTests = 0
  var failedTests = 0
  var erroredTests = 0

  for (file <- new File("src/test/otfspec").listFiles.filter { x => x.getName.endsWith("spec.json") }) {
    processFile(file)
  }

  printSummary()

  def processFile(specFile: File): Unit = {
    println(s"\nProcessing file: ${specFile.getName}")
    val contents = new String(Files.readAllBytes(Paths.get(specFile.getAbsolutePath)))
    val cases = read[List[OTFTestCase]](contents)

    for (testCase <- cases) {
      totalTests += 1

      Try(processCase(testCase)) match {
        case Failure(cause) =>
          erroredTests += 1
          println(s"error")
          cause.printStackTrace()
        case _ =>
      }
    }
  }

  def printSummary(): Unit = {
    val endTime = Instant.now()
    val duration = Duration.between(startTime, endTime)

    val minutes = Math.floor(duration.getSeconds / 60).asInstanceOf[Int]
    val seconds = duration.getSeconds % 60
    val millis = duration.getNano / 1000000

    println(s"\nTest completed in $minutes minute(s), $seconds second(s), $millis millisecond(s)")
    println(s"Total number of tests run: $totalTests")
    println(s"Tests: passed $passedTests, failed $failedTests, errored $erroredTests")
  }

  def processCase(testCase: OTFTestCase): Unit = {
    print(s"Executing test ${testCase.id}: ")

    val originalServerOp: DiscreteOperation = testCase.input.serverOp
    val originalClientOp: DiscreteOperation = testCase.input.clientOp

    val tf = registry.getTransformationFunction(originalServerOp, originalClientOp).get

    (testCase.error, Try(tf.transform(originalServerOp, originalClientOp))) match {
      case (Some(true), Success(_)) =>
        failedTests += 1
        println(s"failed")
        println(s"  Expected error condition, but the transformation succeeded.")
      case (_, Success((sPrime, cPrime))) =>
        val expectedServerOp: DiscreteOperation = testCase.output.get.serverOp
        val expectedClientOp: DiscreteOperation = testCase.output.get.clientOp

        if (sPrime != expectedServerOp || cPrime != expectedClientOp) {
          failedTests += 1
          println(s"failed")
          println(s"  Expected Server Op: $expectedServerOp")
          println(s"  Produced Server Op: $sPrime\n")

          println(s"  Expected Client Op: $expectedClientOp")
          println(s"  Produced Client Op: $cPrime")
        } else {
          passedTests += 1
          println(s"passed")
        }
      case (Some(true), Failure(cause)) =>
        passedTests += 1
        println(s"passed")
      case (_, Failure(cause)) =>
        throw cause
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