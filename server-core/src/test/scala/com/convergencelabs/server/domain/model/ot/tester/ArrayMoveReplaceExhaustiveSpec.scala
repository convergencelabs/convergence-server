package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayMoveReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArrayMoveOperation"
  val clientOperationType: String = "ArrayRepalceOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayReplaceOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(valueId, false, r.fromIndex, r.toIndex),
      ArrayReplaceOperation(valueId, false, i, JString("X")))
  }

  def transform(s: ArrayMoveOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveReplaceTF.transform(s, c)
  }
}
