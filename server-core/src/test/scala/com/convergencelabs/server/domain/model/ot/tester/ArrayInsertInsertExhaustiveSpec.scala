package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayInsertInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayInsertOperation] {

  val serverOperationType: String = "ArrayInsertOperation"
  val clientOperationType: String = "ArrayInsertOperation"
  
  val valueId = "testId"

  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayInsertOperation]] = {
    val indices = generateIndices()
    val value1 = StringValue("vid1", "value1")
    val value2 = StringValue("vid2", "value2")
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayInsertOperation(valueId, false, i1, value1),
      ArrayInsertOperation(valueId, false, i2, value2))
  }

  def transform(s: ArrayInsertOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertInsertTF.transform(s, c)
  }
}
