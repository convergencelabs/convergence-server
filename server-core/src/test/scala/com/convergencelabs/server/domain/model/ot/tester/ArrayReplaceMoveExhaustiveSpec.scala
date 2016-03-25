package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayReplaceMoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayMoveOperation] {

  val serverOperationType: String = "ArrayReplaceOperation"
  val clientOperationType: String = "ArrayMoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayMoveOperation]] = {
    val value = StringValue("vid", "value")
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayReplaceOperation(valueId, false, i, value),
      ArrayMoveOperation(valueId, false, r.fromIndex, r.toIndex))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceMoveTF.transform(s, c)
  }
}
