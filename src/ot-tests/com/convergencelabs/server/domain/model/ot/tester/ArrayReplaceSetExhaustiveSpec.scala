package com.convergencelabs.convergence.server.domain.model.ot

import com.convergencelabs.convergence.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value1
import ArrayOperationExhaustiveSpec.ArrayValue

class ArrayReplaceSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArraySetOperation] {

  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArraySetOperation]] = {
    val value1 = StringValue("vid", "value")
    val value2 = List(StringValue("vid2", "value2"))
    for { i <- generateIndices() } yield TransformationCase(
      ArrayReplaceOperation(ValueId, false, i, Value1),
      ArraySetOperation(ValueId, false, ArrayValue))
  }

  def transform(s: ArrayReplaceOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceSetTF.transform(s, c)
  }
}
