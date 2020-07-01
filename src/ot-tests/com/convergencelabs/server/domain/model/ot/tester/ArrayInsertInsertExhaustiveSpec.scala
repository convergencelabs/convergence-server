package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import ArrayOperationExhaustiveSpec.Value1
import ArrayOperationExhaustiveSpec.Value2
import OperationPairExhaustiveSpec.ValueId

class ArrayInsertInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayInsertOperation] {

  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayInsertOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayInsertOperation(ValueId, false, i1, Value1),
      ArrayInsertOperation(ValueId, false, i2, Value2))
  }

  def transform(s: ArrayInsertOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertInsertTF.transform(s, c)
  }
}
