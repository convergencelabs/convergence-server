package com.convergencelabs.server.domain.model.ot

class ArrayMoveRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayRemoveOperation] {

  val serverOperationType: String = "ArrayMoveOperation"
  val clientOperationType: String = "ArrayRemoveOperation"

  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayRemoveOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(List(), false, r.fromIndex, r.toIndex),
      ArrayRemoveOperation(List(), false, i))
  }

  def transform(s: ArrayMoveOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveRemoveTF.transform(s, c)
  }
}
