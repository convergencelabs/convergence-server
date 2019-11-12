package com.convergencelabs.convergence.server.domain.model.ot

import com.convergencelabs.convergence.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.ArrayValue
import ArrayOperationExhaustiveSpec.Value1

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
