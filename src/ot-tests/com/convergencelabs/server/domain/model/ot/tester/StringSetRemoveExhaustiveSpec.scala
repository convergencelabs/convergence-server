package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class StringSetRemoveExhaustiveSpec extends StringOperationExhaustiveSpec[StringSetOperation, StringRemoveOperation] {

  def generateCases(): List[TransformationCase[StringSetOperation, StringRemoveOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      StringSetOperation(ValueId, false, "SetString"),
      StringRemoveOperation(ValueId, false, i, "Y"))
  }

  def transform(s: StringSetOperation, c: StringRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSetRemoveTF.transform(s, c)
  }
}
