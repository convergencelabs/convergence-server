package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JsonAST.JArray
import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.Value1
import ArrayOperationExhaustiveSpec.ArrayValue

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
