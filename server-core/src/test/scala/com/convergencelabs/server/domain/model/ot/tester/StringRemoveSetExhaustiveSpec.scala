package com.convergencelabs.server.domain.model.ot

class StringRemoveSetExhaustiveSpec extends StringOperationExhaustiveSpec[StringRemoveOperation, StringSetOperation] {

  val serverOperationType: String = "StringRemoveOperation"
  val clientOperationType: String = "StringSetOperation"

  def generateCases(): List[TransformationCase[StringRemoveOperation, StringSetOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      StringRemoveOperation(List(), false, i, "Y"),
      StringSetOperation(List(), false, "SetString"))
  }

  def transform(s: StringRemoveOperation, c: StringSetOperation): (DiscreteOperation, DiscreteOperation) = {
    StringRemoveSetTF.transform(s, c)
  }
}
