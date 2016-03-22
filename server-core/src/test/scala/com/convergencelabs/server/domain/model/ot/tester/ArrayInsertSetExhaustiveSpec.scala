package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JsonAST.JArray

class ArrayInsertSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArraySetOperation] {

  val serverOperationType: String = "ArrayInsertOperation"
  val clientOperationType: String = "ArraySetOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArraySetOperation]] = {
    for { i1 <- generateIndices() } yield TransformationCase(
      ArrayInsertOperation(valueId, false, i1, JString("Y")),
      ArraySetOperation(valueId, false, JArray(List(JString("X")))))
  }

  def transform(s: ArrayInsertOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertSetTF.transform(s, c)
  }

}
