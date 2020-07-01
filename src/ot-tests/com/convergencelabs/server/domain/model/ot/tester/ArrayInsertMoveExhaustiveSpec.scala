package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue
import org.json4s.JString

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
