package com.convergencelabs.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class ArrayRemoveMoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArrayMoveOperation] {

  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArrayMoveOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayRemoveOperation(ValueId, false, i),
      ArrayMoveOperation(ValueId, false, r.fromIndex, r.toIndex))
  }

  def transform(s: ArrayRemoveOperation, c: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveMoveTF.transform(s, c)
  }
}
