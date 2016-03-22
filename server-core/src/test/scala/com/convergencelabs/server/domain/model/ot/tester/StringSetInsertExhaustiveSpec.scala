package com.convergencelabs.server.domain.model.ot

class StringSetInsertExhaustiveSpec extends StringOperationExhaustiveSpec[StringSetOperation, StringInsertOperation] {

  val serverOperationType: String = "StringSetOperation"
  val clientOperationType: String = "StringInsertOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[StringSetOperation, StringInsertOperation]] = {
    for { i1 <- generateIndices() } yield TransformationCase(
      StringSetOperation(valueId, false, "SetString"),
      StringInsertOperation(valueId, false, i1, "Y"))
  }

  def transform(s: StringSetOperation, c: StringInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSetInsertTF.transform(s, c)
  }
}
