package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JsonAST.JArray

class ArraySetReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArraySetOperation"
  val clientOperationType: String = "ArrayReplaceOperation"

  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayReplaceOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(List(), false, JArray(List(JString("X")))),
      ArrayReplaceOperation(List(), false, i, JString("Y")))
  }

  def transform(s: ArraySetOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetReplaceTF.transform(s, c)
  }
}
