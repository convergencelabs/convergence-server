package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import com.convergencelabs.convergence.server.model.domain.model.StringValue
import org.json4s.JString
import org.json4s.JsonAST.JArray

class ArrayInsertSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArraySetOperation] {

  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArraySetOperation]] = {
    for { i1 <- generateIndices() } yield TransformationCase(
      ArrayInsertOperation(ValueId, false, i1, Value1),
      ArraySetOperation(ValueId, false, ArrayValue))
  }

  def transform(s: ArrayInsertOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertSetTF.transform(s, c)
  }

}
