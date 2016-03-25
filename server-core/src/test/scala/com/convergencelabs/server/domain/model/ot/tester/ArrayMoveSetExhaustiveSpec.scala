package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JString
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayMoveSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArraySetOperation] {

  val serverOperationType: String = "ArrayMoveOperation"
  val clientOperationType: String = "ArraySetOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArraySetOperation]] = {
    val value = List(StringValue("vid", "X"))
    for { r <- generateMoveRanges() } yield TransformationCase(
      ArrayMoveOperation(valueId, false, r.fromIndex, r.toIndex),
      ArraySetOperation(valueId, false, value))
  }

  def transform(s: ArrayMoveOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveSetTF.transform(s, c)
  }
}
