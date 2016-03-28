package com.convergencelabs.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class ArrayMoveMoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayMoveOperation] {

  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayMoveOperation]] = {
    val ranges = generateMoveRanges()
    for { r1 <- ranges; r2 <- ranges } yield TransformationCase(
      ArrayMoveOperation(ValueId, false, r1.fromIndex, r1.toIndex),
      ArrayMoveOperation(ValueId, false, r2.fromIndex, r2.toIndex))
  }

  def transform(s: ArrayMoveOperation, c: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveMoveTF.transform(s, c)
  }
}
