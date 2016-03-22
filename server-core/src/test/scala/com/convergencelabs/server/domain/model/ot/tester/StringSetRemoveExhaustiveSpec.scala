package com.convergencelabs.server.domain.model.ot

class StringSetRemoveExhaustiveSpec extends StringOperationExhaustiveSpec[StringSetOperation, StringRemoveOperation] {

  val serverOperationType: String = "StringSetOperation"
  val clientOperationType: String = "StringRemoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[StringSetOperation, StringRemoveOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      StringSetOperation(valueId, false, "SetString"),
      StringRemoveOperation(valueId, false, i, "Y"))
  }

  def transform(s: StringSetOperation, c: StringRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSetRemoveTF.transform(s, c)
  }
}
