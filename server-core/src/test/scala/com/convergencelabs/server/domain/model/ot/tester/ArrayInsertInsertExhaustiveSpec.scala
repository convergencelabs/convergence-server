package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayInsertInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayInsertOperation] {

  val serverOperationType: String = "ArrayInsertOperation"
  val clientOperationType: String = "ArrayInsertOperation"

  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayInsertOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayInsertOperation(List(), false, i1, JString("Y")),
      ArrayInsertOperation(List(), false, i2, JString("X")))
  }
  
  def transform(s: ArrayInsertOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertInsertTF.transform(s, c)
  }
}
