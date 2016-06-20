package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString
import com.convergencelabs.server.domain.model.data.StringValue
import OperationPairExhaustiveSpec.ValueId
import ArrayOperationExhaustiveSpec.ArrayValue

class ArrayMoveSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArraySetOperation] {

  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArraySetOperation]] = {
    for { r <- generateMoveRanges() } yield TransformationCase(
      ArrayMoveOperation(ValueId, false, r.fromIndex, r.toIndex),
      ArraySetOperation(ValueId, false, ArrayValue))
  }

  def transform(s: ArrayMoveOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveSetTF.transform(s, c)
  }
}
