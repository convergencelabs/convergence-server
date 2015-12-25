package com.convergencelabs.server.domain.model.ot

import scala.collection.mutable.ListBuffer
import scala.util.Failure
import scala.util.Success
import org.scalatest.FunSuite
import org.scalatest.FunSpec
import scala.util.Try

trait OperationPairExhaustiveSpec[M <: MockModel, S <: DiscreteOperation, C <: DiscreteOperation] extends FunSpec {

  protected def serverOperationType: String
  protected def clientOperationType: String

  protected def generateCases(): List[TransformationCase[S, C]]
  
  describe(s"When transforming and applying a server ${serverOperationType} against a client ${clientOperationType}") {
    val cases = generateCases()
    cases.foreach { c =>
     it(s"a server ${c.serverOp} and a client ${c.clientOp} must converge") {
        evaluateTransformationCase(c) match {
          case Success(Converged(trace)) =>
          case Success(NotConverged(trace)) =>
            fail()
            println(s"\nFailure: (${trace.serverOp}, ${trace.clientOp})")
            println(s"  Initial State     : ${trace.initialState}\n")
            println(s"  Server Original Op: ${trace.serverOp}")
            println(s"  Server Local State: ${trace.serverLocalState}")
            println(s"  Client xFormed Op : ${trace.cPrime}")
            println(s"  Server Final State: ${trace.serverEndState}\n")

            println(s"  Client Original Op: ${trace.clientOp}")
            println(s"  Client Local State: ${trace.clientLocalState}")
            println(s"  Server xFormed Op : ${trace.sPrime}")
            println(s"  Client Final State: ${trace.clientEndState}\n")

          case Failure(cause) =>
            cause.printStackTrace()
            fail()
        }
      }
    }
  }
  
  def createMockModel(): M
  def transform(s: S, c: C):  (DiscreteOperation, DiscreteOperation)
  
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
      clientEndState.toString()
    )
    
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
