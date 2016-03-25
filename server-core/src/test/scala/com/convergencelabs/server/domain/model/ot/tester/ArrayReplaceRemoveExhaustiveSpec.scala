package com.convergencelabs.server.domain.model.ot

import org.json4s.JString
import com.convergencelabs.server.domain.model.data.StringValue

class ArrayReplaceRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayRemoveOperation] {

  val serverOperationType: String = "ArrayReplaceOperation"
  val clientOperationType: String = "ArrayRemoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayRemoveOperation]] = {
    val value = StringValue("vid", "value")
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayReplaceOperation(valueId, false, i1, value),
      ArrayRemoveOperation(valueId, false, i2))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceRemoveTF.transform(s, c)
  }
}
