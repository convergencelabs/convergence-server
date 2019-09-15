package com.convergencelabs.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class ArraySetSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArraySetOperation] {

  def generateCases(): List[TransformationCase[ArraySetOperation, ArraySetOperation]] = {
    for {
      v1 <- generateValues()
      v2 <- generateValues()
    } yield TransformationCase(
      ArraySetOperation(ValueId, false, List(v1)),
      ArraySetOperation(ValueId, false, List(v2)))
  }

  def transform(s: ArraySetOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetSetTF.transform(s, c)
  }
}
