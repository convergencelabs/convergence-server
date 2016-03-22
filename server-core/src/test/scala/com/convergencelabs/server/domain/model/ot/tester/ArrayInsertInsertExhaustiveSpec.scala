package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayInsertInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayInsertOperation] {

  val serverOperationType: String = "ArrayInsertOperation"
  val clientOperationType: String = "ArrayInsertOperation"
  
  val valueId = "testId"

  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayInsertOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayInsertOperation(valueId, false, i1, JString("Y")),
      ArrayInsertOperation(valueId, false, i2, JString("X")))
  }

  def transform(s: ArrayInsertOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertInsertTF.transform(s, c)
  }
}
