/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.model.ot.xform

import com.convergencelabs.convergence.server.backend.services.domain.model.ot._
import com.convergencelabs.convergence.server.test.tags.ExhaustiveOTTest
import grizzled.slf4j.Logging
import org.scalatest.funspec.AnyFunSpec

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object OperationPairExhaustiveSpec {
  val ValueId = "testId"
}

abstract class OperationPairExhaustiveSpec[M <: MockModel, S <: DiscreteOperation, C <: DiscreteOperation](implicit s: ClassTag[S], c: ClassTag[C])
    extends AnyFunSpec
    with Logging {

  val serverOperationType: String = s.runtimeClass.getSimpleName
  val clientOperationType: String = c.runtimeClass.getSimpleName

  protected def generateCases(): List[TransformationCase[S, C]]

  // scalastyle:off multiple.string.literals
  describe(s"When transforming and applying a server $serverOperationType against a client $clientOperationType") {
    val cases = generateCases()
    cases.foreach { c =>
      it(s"a server ${c.serverOp} and a client ${c.clientOp} must converge", ExhaustiveOTTest) {
        evaluateTransformationCase(c) match {
          case Success(Converged(_)) =>
          case Success(NotConverged(trace)) =>
            val message =
              s"""
               |Failure: (${trace.serverOp}, ${trace.clientOp})
               |  Initial State     : ${trace.initialState}
               |
               |  Server Original Op: ${trace.serverOp}
               |  Server Local State: ${trace.serverLocalState}
               |  Client xFormed Op : ${trace.cPrime}
               |  Server Final State: ${trace.serverEndState}
               |
               |  Client Original Op: ${trace.clientOp}
               |  Client Local State: ${trace.clientLocalState}
               |  Server xFormed Op : ${trace.sPrime}
               |  Client Final State: ${trace.clientEndState}
               |
               |""".stripMargin

            fail(message)
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
    val clientLocalState = clientModel.getData()

    val (sPrime, cPrime) = transform(s, c)

    serverModel.processOperation(cPrime)
    val serverEndState = serverModel.getData()

    clientModel.processOperation(sPrime)
    val clientEndState = clientModel.getData()

    val trace = TestTrace(
      initialState.toString,

      s,
      serverLocalState.toString,
      cPrime,
      serverEndState.toString,

      c,
      clientLocalState.toString,
      sPrime,
      clientEndState.toString)

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
