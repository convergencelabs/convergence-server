package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayReplaceRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayReplaceOperation, ArrayRemoveOperation] {

  val serverOperationType: String = "ArrayReplaceOperation"
  val clientOperationType: String = "ArrayRemoveOperation"

  def generateCases(): List[TransformationCase[ArrayReplaceOperation, ArrayRemoveOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayReplaceOperation(List(), false, i1, JString("Y")),
      ArrayRemoveOperation(List(), false, i2))
  }

  def transform(s: ArrayReplaceOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayReplaceRemoveTF.transform(s, c)
  }
}
