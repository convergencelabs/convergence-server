package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value1

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
