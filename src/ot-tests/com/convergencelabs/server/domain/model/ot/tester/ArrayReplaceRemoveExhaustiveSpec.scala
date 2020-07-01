package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue
import org.json4s.JString

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
