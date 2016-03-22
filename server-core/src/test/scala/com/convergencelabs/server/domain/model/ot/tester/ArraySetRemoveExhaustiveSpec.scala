package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JArray

class ArraySetRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayRemoveOperation] {

  val serverOperationType: String = "ArraySetOperation"
  val clientOperationType: String = "ArrayRemoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayRemoveOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(valueId, false, JArray(List(JString("X")))),
      ArrayRemoveOperation(valueId, false, i))
  }

  def transform(s: ArraySetOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetRemoveTF.transform(s, c)
  }
}
