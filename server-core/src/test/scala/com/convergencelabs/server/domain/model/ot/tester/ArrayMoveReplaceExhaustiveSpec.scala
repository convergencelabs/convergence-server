package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayMoveReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArrayMoveOperation"
  val clientOperationType: String = "ArrayRepalceOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayReplaceOperation]] = {
    val value = StringValue("vid", "value")
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(valueId, false, r.fromIndex, r.toIndex),
      ArrayReplaceOperation(valueId, false, i, value))
  }

  def transform(s: ArrayMoveOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveReplaceTF.transform(s, c)
  }
}
