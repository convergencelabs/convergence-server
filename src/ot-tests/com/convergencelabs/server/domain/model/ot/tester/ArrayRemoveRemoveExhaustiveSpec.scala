package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class ArrayRemoveRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArrayRemoveOperation] {

  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArrayRemoveOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayRemoveOperation(ValueId, false, i1),
      ArrayRemoveOperation(ValueId, false, i2))
  }

  def transform(s: ArrayRemoveOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveRemoveTF.transform(s, c)
  }
}
