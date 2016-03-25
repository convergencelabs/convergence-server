package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JArray
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayReplaceSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArraySetOperation] {

  val serverOperationType: String = "ArrayReplaceOperation"
  val clientOperationType: String = "ArraySetOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArraySetOperation]] = {
    val value1 = StringValue("vid", "value")
    val value2 = List(StringValue("vid2", "value2"))
    for { i <- generateIndices() } yield TransformationCase(
      ArrayReplaceOperation(valueId, false, i, value1),
      ArraySetOperation(valueId, false, value2))
  }

  def transform(s: ArrayReplaceOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceSetTF.transform(s, c)
  }
}
