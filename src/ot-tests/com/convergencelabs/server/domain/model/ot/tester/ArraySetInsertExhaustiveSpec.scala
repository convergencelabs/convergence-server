package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue

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
