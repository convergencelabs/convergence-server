package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayRemoveInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArrayInsertOperation] {

  val serverOperationType: String = "ArrayRemoveOperation"
  val clientOperationType: String = "ArrayInsertOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArrayInsertOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayRemoveOperation(valueId, false, i1),
      ArrayInsertOperation(valueId, false, i2, JString("X")))
  }

  def transform(s: ArrayRemoveOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveInsertTF.transform(s, c)
  }
}
