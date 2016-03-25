package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JsonAST.JArray
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayInsertSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArraySetOperation] {

  val serverOperationType: String = "ArrayInsertOperation"
  val clientOperationType: String = "ArraySetOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArraySetOperation]] = {
    val value = StringValue("vid1", "Y")
    val value2 = List(StringValue("vid2", "X"))
    for { i1 <- generateIndices() } yield TransformationCase(
      ArrayInsertOperation(valueId, false, i1, value),
      ArraySetOperation(valueId, false, value2))
  }

  def transform(s: ArrayInsertOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertSetTF.transform(s, c)
  }

}
