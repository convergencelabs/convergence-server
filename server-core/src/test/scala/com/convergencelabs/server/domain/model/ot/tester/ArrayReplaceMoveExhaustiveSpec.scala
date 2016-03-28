package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value1

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
