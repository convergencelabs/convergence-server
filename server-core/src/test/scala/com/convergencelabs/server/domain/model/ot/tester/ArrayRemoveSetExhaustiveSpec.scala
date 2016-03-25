package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JArray
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayRemoveSetExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArraySetOperation] {

  val serverOperationType: String = "ArrayRemoveOperation"
  val clientOperationType: String = "ArraySetOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArraySetOperation]] = {
    val value = List(StringValue("vid", "value"))
    for { i <- generateIndices() } yield TransformationCase(
      ArrayRemoveOperation(valueId, false, i),
      ArraySetOperation(valueId, false, value))
  }

  def transform(s: ArrayRemoveOperation, c: ArraySetOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveSetTF.transform(s, c)
  }
}
