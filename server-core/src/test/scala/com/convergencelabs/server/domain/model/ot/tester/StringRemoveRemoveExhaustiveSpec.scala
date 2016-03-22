package com.convergencelabs.server.domain.model.ot

class StringRemoveRemoveExhaustiveSpec extends StringOperationExhaustiveSpec[StringRemoveOperation, StringRemoveOperation] {

  val serverOperationType: String = "StringRemoveOperation"
  val clientOperationType: String = "StringRemoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[StringRemoveOperation, StringRemoveOperation]] = {
    val ranges = generateRemoveRanges()
    for { r1 <- ranges; r2 <- ranges } yield TransformationCase(
      StringRemoveOperation(valueId, false, r1.index, r1.value),
      StringRemoveOperation(valueId, false, r2.index, r2.value))
  }

  def transform(s: StringRemoveOperation, c: StringRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    StringRemoveRemoveTF.transform(s, c)
  }
}
