package com.convergencelabs.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class StringSetInsertExhaustiveSpec extends StringOperationExhaustiveSpec[StringSetOperation, StringInsertOperation] {

  def generateCases(): List[TransformationCase[StringSetOperation, StringInsertOperation]] = {
    for { i1 <- generateIndices() } yield TransformationCase(
      StringSetOperation(ValueId, false, "SetString"),
      StringInsertOperation(ValueId, false, i1, "Y"))
  }

  def transform(s: StringSetOperation, c: StringInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSetInsertTF.transform(s, c)
  }
}
