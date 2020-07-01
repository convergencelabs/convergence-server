package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue
import org.json4s.JString

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
