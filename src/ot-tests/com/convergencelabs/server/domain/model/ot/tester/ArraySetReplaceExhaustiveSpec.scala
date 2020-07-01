package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue

class ArraySetReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayReplaceOperation] {

  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayReplaceOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(ValueId, false, ArrayValue),
      ArrayReplaceOperation(ValueId, false, i, Value1))
  }

  def transform(s: ArraySetOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetReplaceTF.transform(s, c)
  }
}
