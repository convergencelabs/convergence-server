package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue

class ArraySetRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayRemoveOperation] {

  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayRemoveOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(ValueId, false, ArrayValue),
      ArrayRemoveOperation(ValueId, false, i))
  }

  def transform(s: ArraySetOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetRemoveTF.transform(s, c)
  }
}
