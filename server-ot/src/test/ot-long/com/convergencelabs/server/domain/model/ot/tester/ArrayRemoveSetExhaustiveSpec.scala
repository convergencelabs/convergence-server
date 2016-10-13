package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JArray
import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.ArrayValue

class ArrayRemoveSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArraySetOperation] {

  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArraySetOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArrayRemoveOperation(ValueId, false, i),
      ArraySetOperation(ValueId, false, ArrayValue))
  }

  def transform(s: ArrayRemoveOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveSetTF.transform(s, c)
  }
}
