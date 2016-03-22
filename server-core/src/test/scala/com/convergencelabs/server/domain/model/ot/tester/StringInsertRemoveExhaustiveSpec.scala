package com.convergencelabs.server.domain.model.ot

class StringInsertRemoveExhaustiveSpec extends StringOperationExhaustiveSpec[StringInsertOperation, StringRemoveOperation] {

  val serverOperationType: String = "StringInsertOperation"
  val clientOperationType: String = "StringRemoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[StringInsertOperation, StringRemoveOperation]] = {
    for { i <- generateIndices(); r <- generateRemoveRanges() } yield TransformationCase(
      StringInsertOperation(valueId, false, i, "Y"),
      StringRemoveOperation(valueId, false, r.index, r.value))
  }

  def transform(s: StringInsertOperation, c: StringRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    StringInsertRemoveTF.transform(s, c)
  }
}
