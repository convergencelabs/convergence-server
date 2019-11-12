package com.convergencelabs.convergence.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class StringRemoveSetExhaustiveSpec extends StringOperationExhaustiveSpec[StringRemoveOperation, StringSetOperation] {

  def generateCases(): List[TransformationCase[StringRemoveOperation, StringSetOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      StringRemoveOperation(ValueId, false, i, "Y"),
      StringSetOperation(ValueId, false, "SetString"))
  }

  def transform(s: StringRemoveOperation, c: StringSetOperation): (DiscreteOperation, DiscreteOperation) = {
    StringRemoveSetTF.transform(s, c)
  }
}
