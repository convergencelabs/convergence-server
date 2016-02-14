package com.convergencelabs.server.domain.model.ot

class StringSetInsertExhaustiveSpec extends StringOperationExhaustiveSpec[StringSetOperation, StringInsertOperation] {

  val serverOperationType: String = "StringSetOperation"
  val clientOperationType: String = "StringInsertOperation"

  def generateCases(): List[TransformationCase[StringSetOperation, StringInsertOperation]] = {
    for { i1 <- generateIndices() } yield TransformationCase(
      StringSetOperation(List(), false, "SetString"),
      StringInsertOperation(List(), false, i1, "Y"))
  }

  def transform(s: StringSetOperation, c: StringInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSetInsertTF.transform(s, c)
  }
}
