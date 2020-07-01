package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue

class ArraySetMoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayMoveOperation] {

  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayMoveOperation]] = {
    for { r <- generateMoveRanges() } yield TransformationCase(
      ArraySetOperation(ValueId, false, ArrayValue),
      ArrayMoveOperation(ValueId, false, r.fromIndex, r.toIndex))
  }

  def transform(s: ArraySetOperation, c: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetMoveTF.transform(s, c)
  }
}
