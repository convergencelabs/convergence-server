package com.convergencelabs.convergence.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.convergence.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value2
import ArrayOperationExhaustiveSpec.Value1

class ArrayInsertReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayReplaceOperation]() {

  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayReplaceOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayInsertOperation(ValueId, false, i1, Value1),
      ArrayReplaceOperation(ValueId, false, i2, Value2))
  }

  def transform(s: ArrayInsertOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertReplaceTF.transform(s, c)
  }
}
