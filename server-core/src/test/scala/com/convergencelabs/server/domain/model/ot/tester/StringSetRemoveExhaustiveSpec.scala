package com.convergencelabs.server.domain.model.ot

class StringSetRemoveExhaustiveSpec extends StringOperationExhaustiveSpec[StringSetOperation, StringRemoveOperation] {

  val serverOperationType: String = "StringSetOperation"
  val clientOperationType: String = "StringRemoveOperation"

  def generateCases(): List[TransformationCase[StringSetOperation, StringRemoveOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      StringSetOperation(List(), false, "SetString"),
      StringRemoveOperation(List(), false, i, "Y"))
  }

  def transform(s: StringSetOperation, c: StringRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSetRemoveTF.transform(s, c)
  }
}
