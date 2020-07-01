package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue

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
