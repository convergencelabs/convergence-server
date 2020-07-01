package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue
import org.json4s.JString

class ArrayReplaceMoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayMoveOperation] {

  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayMoveOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayReplaceOperation(ValueId, false, i, Value1),
      ArrayMoveOperation(ValueId, false, r.fromIndex, r.toIndex))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceMoveTF.transform(s, c)
  }
}
