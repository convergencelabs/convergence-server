package com.convergencelabs.convergence.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.convergence.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value1

class ArrayRemoveInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArrayInsertOperation] {

  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArrayInsertOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayRemoveOperation(ValueId, false, i1),
      ArrayInsertOperation(ValueId, false, i2, Value1))
  }

  def transform(s: ArrayRemoveOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveInsertTF.transform(s, c)
  }
}
