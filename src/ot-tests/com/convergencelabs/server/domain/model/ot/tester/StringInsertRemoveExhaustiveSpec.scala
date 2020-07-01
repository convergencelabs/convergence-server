package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class StringInsertRemoveExhaustiveSpec extends StringOperationExhaustiveSpec[StringInsertOperation, StringRemoveOperation] {

  def generateCases(): List[TransformationCase[StringInsertOperation, StringRemoveOperation]] = {
    for { i <- generateIndices(); r <- generateRemoveRanges() } yield TransformationCase(
      StringInsertOperation(ValueId, false, i, "Y"),
      StringRemoveOperation(ValueId, false, r.index, r.value))
  }

  def transform(s: StringInsertOperation, c: StringRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    StringInsertRemoveTF.transform(s, c)
  }
}
