package com.convergencelabs.server.domain.model.ot

class StringInsertSetExhaustiveSpec extends StringOperationExhaustiveSpec[StringInsertOperation, StringSetOperation] {

  val serverOperationType: String = "StringInsertOperation"
  val clientOperationType: String = "StringSetOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[StringInsertOperation, StringSetOperation]] = {
    for { i <- generateIndices() } yield TransformationCase(
      StringInsertOperation(valueId, false, i, "Y"),
      StringSetOperation(valueId, false, "SetString"))
  }

  def transform(s: StringInsertOperation, c: StringSetOperation): (DiscreteOperation, DiscreteOperation) = {
    StringInsertSetTF.transform(s, c)
  }
}
