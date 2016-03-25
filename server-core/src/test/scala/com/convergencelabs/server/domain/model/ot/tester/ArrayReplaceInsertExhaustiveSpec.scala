package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayReplaceInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayInsertOperation] {

  val serverOperationType: String = "ArrayReplaceOperation"
  val clientOperationType: String = "ArrayInsertOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayInsertOperation]] = {
    val value1 = StringValue("vid1", "value1")
    val value2 = StringValue("vid2", "value2")
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayReplaceOperation(valueId, false, i1, value1),
      ArrayInsertOperation(valueId, false, i2, value2))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceInsertTF.transform(s, c)
  }
}
