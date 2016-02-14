package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayMoveReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArrayMoveOperation"
  val clientOperationType: String = "ArrayRepalceOperation"

  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayReplaceOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(List(), false, r.fromIndex, r.toIndex),
      ArrayReplaceOperation(List(), false, i, JString("X")))
  }

  def transform(s: ArrayMoveOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveReplaceTF.transform(s, c)
  }
}
