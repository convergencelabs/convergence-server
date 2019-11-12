package com.convergencelabs.convergence.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class StringRemoveRemoveExhaustiveSpec extends StringOperationExhaustiveSpec[StringRemoveOperation, StringRemoveOperation] {

  def generateCases(): List[TransformationCase[StringRemoveOperation, StringRemoveOperation]] = {
    val ranges = generateRemoveRanges()
    for { r1 <- ranges; r2 <- ranges } yield TransformationCase(
      StringRemoveOperation(ValueId, false, r1.index, r1.value),
      StringRemoveOperation(ValueId, false, r2.index, r2.value))
  }

  def transform(s: StringRemoveOperation, c: StringRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    StringRemoveRemoveTF.transform(s, c)
  }
}
