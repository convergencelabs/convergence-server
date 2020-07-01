package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue
import org.json4s.JString

class ArrayMoveReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayReplaceOperation] {

  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayReplaceOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(ValueId, false, r.fromIndex, r.toIndex),
      ArrayReplaceOperation(ValueId, false, i, Value1))
  }

  def transform(s: ArrayMoveOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveReplaceTF.transform(s, c)
  }
}
