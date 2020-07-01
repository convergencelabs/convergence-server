package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue
import org.json4s.JString

class ArrayRemoveReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArrayReplaceOperation] {

  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArrayReplaceOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayRemoveOperation(ValueId, false, i1),
      ArrayReplaceOperation(ValueId, false, i2, Value1))
  }

  def transform(s: ArrayRemoveOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveReplaceTF.transform(s, c)
  }
}
