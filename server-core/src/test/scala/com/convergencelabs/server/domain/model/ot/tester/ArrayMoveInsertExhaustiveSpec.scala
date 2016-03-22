package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayMoveInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayInsertOperation] {

  val serverOperationType: String = "ArrayMoveOperation"
  val clientOperationType: String = "ArrayInsertOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayInsertOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(valueId, false, r.fromIndex, r.toIndex),
      ArrayInsertOperation(valueId, false, i, JString("X")))
  }

  def transform(s: ArrayMoveOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveInsertTF.transform(s, c)
  }
}
