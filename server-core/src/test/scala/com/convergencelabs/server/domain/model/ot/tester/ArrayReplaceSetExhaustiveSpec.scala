package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JArray

class ArrayReplaceSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArraySetOperation] {

  val serverOperationType: String = "ArrayReplaceOperation"
  val clientOperationType: String = "ArraySetOperation"

  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArraySetOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArrayReplaceOperation(List(), false, i, JString("Y")),
      ArraySetOperation(List(), false, JArray(List(JString("X")))))
  }

  def transform(s: ArrayReplaceOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceSetTF.transform(s, c)
  }
}
