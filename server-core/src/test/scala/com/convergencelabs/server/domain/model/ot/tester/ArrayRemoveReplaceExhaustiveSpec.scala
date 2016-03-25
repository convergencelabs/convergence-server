package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayRemoveReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArrayRemoveOperation"
  val clientOperationType: String = "ArrayReplaceOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArrayReplaceOperation]] = {
    val indices = generateIndices()
    val value = StringValue("vid", "value")
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayRemoveOperation(valueId, false, i1),
      ArrayReplaceOperation(valueId, false, i2, value))
  }

  def transform(s: ArrayRemoveOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveReplaceTF.transform(s, c)
  }
}
