package com.convergencelabs.server.domain.model.ot

import com.convergencelabs.server.domain.model.data.StringValue


class ArraySetInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArraySetOperation, ArrayInsertOperation] {

  val serverOperationType: String = "ArraySetOperation"
  val clientOperationType: String = "ArrayInsertOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArraySetOperation, ArrayInsertOperation]] = {
    val value1 = StringValue("vid", "value")
    val value2 = List(StringValue("vid2", "value2"))
    
    for { i <- generateIndices() } yield TransformationCase(
      ArraySetOperation(valueId, false, value2),
      ArrayInsertOperation(valueId, false, i, value1))
  }

  def transform(s: ArraySetOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArraySetInsertTF.transform(s, c)
  }
}
