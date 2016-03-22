package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayReplaceInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayInsertOperation] {

  val serverOperationType: String = "ArrayReplaceOperation"
  val clientOperationType: String = "ArrayInsertOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayInsertOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayReplaceOperation(valueId, false, i1, JString("Y")),
      ArrayInsertOperation(valueId, false, i2, JString("X")))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceInsertTF.transform(s, c)
  }
}
