package com.convergencelabs.convergence.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class StringSetSetExhaustiveSpec extends StringOperationExhaustiveSpec[StringSetOperation, StringSetOperation] {

  def generateCases(): List[TransformationCase[StringSetOperation, StringSetOperation]] = {
    List(TransformationCase(
      StringSetOperation(ValueId, false, "ServerString"),
      StringSetOperation(ValueId, false, "ClientString")))
  }

  def transform(s: StringSetOperation, c: StringSetOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSetSetTF.transform(s, c)
  }
}
