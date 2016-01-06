package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JsonAST.JArray

class ArraySetSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArraySetOperation] {

  val serverOperationType: String = "ArraySetOperation"
  val clientOperationType: String = "ArraySetOperation"

  def generateCases(): List[TransformationCase[ArraySetOperation, ArraySetOperation]] = {
    for {
      v1 <- generateValues()
      v2 <- generateValues()
    } yield TransformationCase(
      ArraySetOperation(List(), false, JArray(List(v1))),
      ArraySetOperation(List(), false, JArray(List(v2))))
  }

  def transform(s: ArraySetOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetSetTF.transform(s, c)
  }
}
