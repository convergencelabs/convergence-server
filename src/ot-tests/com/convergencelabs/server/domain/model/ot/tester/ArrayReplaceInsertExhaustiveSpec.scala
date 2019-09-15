package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value1
import ArrayOperationExhaustiveSpec.Value2

class ArrayReplaceInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayInsertOperation] {

  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayInsertOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayReplaceOperation(ValueId, false, i1, Value1),
      ArrayInsertOperation(ValueId, false, i2, Value2))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceInsertTF.transform(s, c)
  }
}
