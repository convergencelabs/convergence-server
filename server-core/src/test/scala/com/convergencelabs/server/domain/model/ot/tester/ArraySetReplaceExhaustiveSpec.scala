package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JsonAST.JArray
import com.convergencelabs.server.domain.model.data.StringValue

class ArraySetReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArraySetOperation"
  val clientOperationType: String = "ArrayReplaceOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayReplaceOperation]] = {
    val value1 = StringValue("vid", "value")
    val value2 = List(StringValue("vid2", "value2"))
    
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(valueId, false, value2),
      ArrayReplaceOperation(valueId, false, i, value1))
  }

  def transform(s: ArraySetOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetReplaceTF.transform(s, c)
  }
}
