package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JArray


class ArraySetInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayInsertOperation] {

  val serverOperationType: String = "ArraySetOperation"
  val clientOperationType: String = "ArrayInsertOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayInsertOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(valueId, false, JArray(List(JString("X")))),
      ArrayInsertOperation(valueId, false, i, JString("Y")))
  }

  def transform(s: ArraySetOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetInsertTF.transform(s, c)
  }
}
