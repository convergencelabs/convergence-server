package com.convergencelabs.server.domain.model.ot

import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value1
import ArrayOperationExhaustiveSpec.ArrayValue

class ArraySetInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayInsertOperation] {

  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayInsertOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(ValueId, false, ArrayValue),
      ArrayInsertOperation(ValueId, false, i, Value1))
  }

  def transform(s: ArraySetOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetInsertTF.transform(s, c)
  }
}
