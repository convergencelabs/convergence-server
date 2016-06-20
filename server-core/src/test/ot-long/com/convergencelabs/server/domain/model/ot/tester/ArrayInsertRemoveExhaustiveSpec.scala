package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId

class ArrayInsertRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayRemoveOperation] {

  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayRemoveOperation]] = {
    val indices = generateIndices()
    val value = StringValue("vid", "value")
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayInsertOperation(ValueId, false, i1, value),
      ArrayRemoveOperation(ValueId, false, i2))
  }

  def transform(s: ArrayInsertOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertRemoveTF.transform(s, c)
  }
}
