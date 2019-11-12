package com.convergencelabs.convergence.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class StringInsertInsertExhaustiveSpec extends StringOperationExhaustiveSpec[StringInsertOperation, StringInsertOperation] {

  def generateCases(): List[TransformationCase[StringInsertOperation, StringInsertOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      StringInsertOperation(ValueId, false, i1, "Y"),
      StringInsertOperation(ValueId, false, i2, "X"))
  }

  def transform(s: StringInsertOperation, c: StringInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    StringInsertInsertTF.transform(s, c)
  }
}
