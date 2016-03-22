package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JsonAST.JArray

class ArraySetReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArraySetOperation"
  val clientOperationType: String = "ArrayReplaceOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayReplaceOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(valueId, false, JArray(List(JString("X")))),
      ArrayReplaceOperation(valueId, false, i, JString("Y")))
  }

  def transform(s: ArraySetOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetReplaceTF.transform(s, c)
  }
}
