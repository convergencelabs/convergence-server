package com.convergencelabs.server.domain.model.ot

class ArrayMoveRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayRemoveOperation] {

  val serverOperationType: String = "ArrayMoveOperation"
  val clientOperationType: String = "ArrayRemoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayRemoveOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(valueId, false, r.fromIndex, r.toIndex),
      ArrayRemoveOperation(valueId, false, i))
  }

  def transform(s: ArrayMoveOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveRemoveTF.transform(s, c)
  }
}
