package com.convergencelabs.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class ArrayMoveRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayRemoveOperation] {
  
  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayRemoveOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(ValueId, false, r.fromIndex, r.toIndex),
      ArrayRemoveOperation(ValueId, false, i))
  }

  def transform(s: ArrayMoveOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveRemoveTF.transform(s, c)
  }
}
