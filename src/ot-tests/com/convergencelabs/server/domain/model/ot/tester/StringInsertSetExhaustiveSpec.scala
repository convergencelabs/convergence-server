package com.convergencelabs.convergence.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class StringInsertSetExhaustiveSpec extends StringOperationExhaustiveSpec[StringInsertOperation, StringSetOperation] {

  def generateCases(): List[TransformationCase[StringInsertOperation, StringSetOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      StringInsertOperation(ValueId, false, i, "Y"),
      StringSetOperation(ValueId, false, "SetString"))
  }

  def transform(s: StringInsertOperation, c: StringSetOperation): (DiscreteOperation, DiscreteOperation) = {
    StringInsertSetTF.transform(s, c)
  }
}
