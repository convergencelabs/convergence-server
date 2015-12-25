package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JArray

class ArraySetRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayRemoveOperation] {

  val serverOperationType: String = "ArraySetOperation"
  val clientOperationType: String = "ArrayRemoveOperation"

  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayRemoveOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(List(), false, JArray(List(JString("X")))),
      ArrayRemoveOperation(List(), false, i))
  }

  def transform(s: ArraySetOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetRemoveTF.transform(s, c)
  }
}
