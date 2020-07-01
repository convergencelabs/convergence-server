package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue
import org.json4s.JString
import org.json4s.JArray

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
