package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value1

class ArrayReplaceRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayRemoveOperation] {

  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayRemoveOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayReplaceOperation(ValueId, false, i1, Value1),
      ArrayRemoveOperation(ValueId, false, i2))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceRemoveTF.transform(s, c)
  }
}
