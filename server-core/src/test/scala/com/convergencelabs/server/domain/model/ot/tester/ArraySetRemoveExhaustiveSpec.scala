package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import org.json4s.JArray
import com.convergencelabs.server.domain.model.data.StringValue

class ArraySetRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayRemoveOperation] {

  val serverOperationType: String = "ArraySetOperation"
  val clientOperationType: String = "ArrayRemoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayRemoveOperation]] = {
    val value = List(StringValue("vid", "value"))
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(valueId, false, value),
      ArrayRemoveOperation(valueId, false, i))
  }

  def transform(s: ArraySetOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetRemoveTF.transform(s, c)
  }
}
