package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayReplaceReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArrayReplaceOperation"
  val clientOperationType: String = "ArrayReplaceOperation"

  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayReplaceOperation]] = {
    val indices = generateIndices()
    val values = generateValues()

    for {
      i1 <- indices
      v1 <- values
      i2 <- indices
      v2 <- values
    } yield TransformationCase(
      ArrayReplaceOperation(List(), false, i1, v1),
      ArrayReplaceOperation(List(), false, i2, v2))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceReplaceTF.transform(s, c)
  }
}
