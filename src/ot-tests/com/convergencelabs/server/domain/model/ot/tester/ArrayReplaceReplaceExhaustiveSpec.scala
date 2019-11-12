package com.convergencelabs.convergence.server.domain.model.ot

import OperationPairExhaustiveSpec.ValueId

class ArrayReplaceReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayReplaceOperation]() {

  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayReplaceOperation]] = {
    val indices = generateIndices()
    val values = generateValues()

    for {
      i1 <- indices
      v1 <- values
      i2 <- indices
      v2 <- values
    } yield TransformationCase(
      ArrayReplaceOperation(ValueId, false, i1, v1),
      ArrayReplaceOperation(ValueId, false, i2, v2))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceReplaceTF.transform(s, c)
  }
}
