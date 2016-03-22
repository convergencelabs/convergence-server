package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JArray

class ArrayRemoveSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArraySetOperation] {

  val serverOperationType: String = "ArrayRemoveOperation"
  val clientOperationType: String = "ArraySetOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArraySetOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArrayRemoveOperation(valueId, false, i),
      ArraySetOperation(valueId, false, JArray(List(JString("X")))))
  }

  def transform(s: ArrayRemoveOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveSetTF.transform(s, c)
  }
}
