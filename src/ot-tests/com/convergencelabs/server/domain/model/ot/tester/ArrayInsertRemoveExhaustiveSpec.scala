package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue
import org.json4s.JString

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
