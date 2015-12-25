package com.convergencelabs.server.domain.model.ot

class StringRemoveInsertExhaustiveSpec extends StringOperationExhaustiveSpec[StringRemoveOperation, StringInsertOperation] {

  val serverOperationType: String = "StringRemoveOperation"
  val clientOperationType: String = "StringInsertOperation"

  def generateCases(): List[TransformationCase[StringRemoveOperation, StringInsertOperation]] = {
    for { i <- generateIndices(); r <- generateRemoveRanges() } yield TransformationCase(
      StringRemoveOperation(List(), false, r.index, r.value),
      StringInsertOperation(List(), false, i, "X"))
  }

  def transform(s: StringRemoveOperation, c: StringInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    StringRemoveInsertTF.transform(s, c)
  }
}
