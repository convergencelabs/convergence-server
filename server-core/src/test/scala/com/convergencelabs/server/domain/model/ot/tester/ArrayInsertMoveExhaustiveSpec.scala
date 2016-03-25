package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayInsertMoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayMoveOperation] {

  val serverOperationType: String = "ArrayInsertOperation"
  val clientOperationType: String = "ArrayMoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayMoveOperation]] = {
    val value = StringValue("vid", "value")
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayInsertOperation(valueId, false, i, value),
      ArrayMoveOperation(valueId, false, r.fromIndex, r.toIndex))
  }

  def transform(s: ArrayInsertOperation, c: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertMoveTF.transform(s, c)
  }
}
