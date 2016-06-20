package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value1

class ArrayInsertMoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayMoveOperation] {
  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayMoveOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayInsertOperation(ValueId, false, i, Value1),
      ArrayMoveOperation(ValueId, false, r.fromIndex, r.toIndex))
  }

  def transform(s: ArrayInsertOperation, c: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertMoveTF.transform(s, c)
  }
}
