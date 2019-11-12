package com.convergencelabs.convergence.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class StringRemoveInsertExhaustiveSpec extends StringOperationExhaustiveSpec[StringRemoveOperation, StringInsertOperation] {

  def generateCases(): List[TransformationCase[StringRemoveOperation, StringInsertOperation]] = {
    for { i <- generateIndices(); r <- generateRemoveRanges() } yield TransformationCase(
      StringRemoveOperation(ValueId, false, r.index, r.value),
      StringInsertOperation(ValueId, false, i, "X"))
  }

  def transform(s: StringRemoveOperation, c: StringInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    StringRemoveInsertTF.transform(s, c)
  }
}
