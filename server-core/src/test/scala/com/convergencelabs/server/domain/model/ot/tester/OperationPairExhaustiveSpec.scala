package com.convergencelabs.server.domain.model.ot

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.scalatest.Finders
import org.scalatest.FunSpec

import grizzled.slf4j.Logging

trait OperationPairExhaustiveSpec[M <: MockModel, S <: DiscreteOperation, C <: DiscreteOperation]
    extends FunSpec
    with Logging {

  protected def serverOperationType: String
  protected def clientOperationType: String

  protected def generateCases(): List[TransformationCase[S, C]]

  // scalastyle:off multiple.string.literals
  describe(s"When transforming and applying a server ${serverOperationType} against a client ${clientOperationType}") {
    val cases = generateCases()
    cases.foreach { c =>
      it(s"a server ${c.serverOp} and a client ${c.clientOp} must converge") {
        evaluateTransformationCase(c) match {
          case Success(Converged(trace)) =>
          case Success(NotConverged(trace)) =>
            logger.info(s"\nFailure: (${trace.serverOp}, ${trace.clientOp})")
            logger.info(s"  Initial State     : ${trace.initialState}\n")
            logger.info(s"  Server Original Op: ${trace.serverOp}")
            logger.info(s"  Server Local State: ${trace.serverLocalState}")
            logger.info(s"  Client xFormed Op : ${trace.cPrime}")
            logger.info(s"  Server Final State: ${trace.serverEndState}\n")

            logger.info(s"  Client Original Op: ${trace.clientOp}")
            logger.info(s"  Client Local State: ${trace.clientLocalState}")
            logger.info(s"  Server xFormed Op : ${trace.sPrime}")
            logger.info(s"  Client Final State: ${trace.clientEndState}\n")
            fail("state did no converge")
          case Failure(cause) =>
            throw cause
        }
      }
    }
  }

  def createMockModel(): M
  def transform(s: S, c: C): (DiscreteOperation, DiscreteOperation)

  def evaluateTransformationCase(tc: TransformationCase[S, C]): Try[TransformationTestResult] = {
    val serverModel = createMockModel()
    val clientModel = createMockModel()

    val initialState = serverModel.getData()

    val s = tc.serverOp
    val c = tc.clientOp

    serverModel.processOperation(s)
    val serverLocalState = serverModel.getData()

    clientModel.processOperation(c)
    val clientLocalState = serverModel.getData()

    val (sPrime, cPrime) = transform(s, c)

    serverModel.processOperation(cPrime)
    val serverEndState = serverModel.getData()

    clientModel.processOperation(sPrime)
    val clientEndState = clientModel.getData()

    val trace = TestTrace(
      initialState.toString(),

      s,
      serverLocalState.toString(),
      cPrime,
      serverEndState.toString(),

      c,
      clientLocalState.toString(),
      sPrime,
      clientEndState.toString())

    if (serverEndState == clientEndState) {
      Success(Converged(trace))
    } else {
      Success(NotConverged(trace))
    }
  }
}

case class TestTrace(
  initialState: String,

  serverOp: Operation,
  serverLocalState: String,
  cPrime: Operation,
  serverEndState: String,

  clientOp: Operation,
  clientLocalState: String,
  sPrime: Operation,
  clientEndState: String)

sealed trait TransformationTestResult
case class Converged(trace: TestTrace) extends TransformationTestResult
case class NotConverged(trace: TestTrace) extends TransformationTestResult

case class TestResult(totalCases: Int, passedCases: Int, failedCases: Int)
