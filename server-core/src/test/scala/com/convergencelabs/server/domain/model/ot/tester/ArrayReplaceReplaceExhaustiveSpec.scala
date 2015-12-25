package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayReplaceReplaceExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayReplaceOperation] {

  val serverOperationType: String = "ArrayReplaceOperation"
  val clientOperationType: String = "ArrayReplaceOperation"

  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayReplaceOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayReplaceOperation(List(), false, i1, JString("Y")),
      ArrayReplaceOperation(List(), false, i2, JString("X")))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayReplaceOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceReplaceTF.transform(s, c)
  }
}
