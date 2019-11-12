package com.convergencelabs.convergence.server.domain.model.ot

import com.convergencelabs.convergence.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.ArrayValue

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
