package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayInsertReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArrayInsertOperation"
  val clientOperationType: String = "ArrayReplaceOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayReplaceOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayInsertOperation(valueId, false, i1, JString("Y")),
      ArrayReplaceOperation(valueId, false, i2, JString("X")))
  }

  def transform(s: ArrayInsertOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertReplaceTF.transform(s, c)
  }
}
