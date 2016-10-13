package com.convergencelabs.server.domain.model.ot

import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.ArrayValue

class ArraySetRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayRemoveOperation] {

  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayRemoveOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(ValueId, false, ArrayValue),
      ArrayRemoveOperation(ValueId, false, i))
  }

  def transform(s: ArraySetOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetRemoveTF.transform(s, c)
  }
}
