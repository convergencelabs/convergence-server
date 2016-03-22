package com.convergencelabs.server.domain.model.ot

class ArrayRemoveMoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArrayMoveOperation] {

  val serverOperationType: String = "ArrayRemoveOperation"
  val clientOperationType: String = "ArrayMoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArrayMoveOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayRemoveOperation(valueId, false, i),
      ArrayMoveOperation(valueId, false, r.fromIndex, r.toIndex))
  }

  def transform(s: ArrayRemoveOperation, c: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveMoveTF.transform(s, c)
  }
}
