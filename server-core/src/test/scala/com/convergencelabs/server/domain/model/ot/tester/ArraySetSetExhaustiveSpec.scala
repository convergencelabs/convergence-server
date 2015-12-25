package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JsonAST.JArray

class ArraySetSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArraySetOperation] {

  val serverOperationType: String = "ArraySetOperation"
  val clientOperationType: String = "ArraySetOperation"

  def generateCases(): List[TransformationCase[ArraySetOperation, ArraySetOperation]] = {
    List(TransformationCase(
      ArraySetOperation(List(), false, JArray(List(JString("X")))),
      ArraySetOperation(List(), false, JArray(List(JString("Y"))))))
  }

  def transform(s: ArraySetOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetSetTF.transform(s, c)
  }
}
