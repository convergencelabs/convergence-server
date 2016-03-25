package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayInsertReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArrayInsertOperation"
  val clientOperationType: String = "ArrayReplaceOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayReplaceOperation]] = {
    val indices = generateIndices()
    val value1 = StringValue("vid1", "value1")
    val value2 = StringValue("vid2", "value2")
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayInsertOperation(valueId, false, i1, value1),
      ArrayReplaceOperation(valueId, false, i2, value2))
  }

  def transform(s: ArrayInsertOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertReplaceTF.transform(s, c)
  }
}
