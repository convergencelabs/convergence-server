package com.convergencelabs.server.domain.model.ot

class StringInsertRemoveExhaustiveSpec extends StringOperationExhaustiveSpec[StringInsertOperation, StringRemoveOperation] {
  
  val serverOperationType: String = "StringInsertOperation"
  val clientOperationType: String = "StringRemoveOperation"

  def generateCases(): List[TransformationCase[StringInsertOperation, StringRemoveOperation]] = {
    for { i <- generateIndices(); r <- generateRemoveRanges() } yield TransformationCase(
      StringInsertOperation(List(), false, i, "Y"),
      StringRemoveOperation(List(), false, r.index, r.value))
  }
  
  def transform(s: StringInsertOperation, c: StringRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    StringInsertRemoveTF.transform(s, c)
  }
}
