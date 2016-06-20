package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value1

class ArrayMoveInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayInsertOperation] {

  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayInsertOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(ValueId, false, r.fromIndex, r.toIndex),
      ArrayInsertOperation(ValueId, false, i, Value1))
  }

  def transform(s: ArrayMoveOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveInsertTF.transform(s, c)
  }
}
