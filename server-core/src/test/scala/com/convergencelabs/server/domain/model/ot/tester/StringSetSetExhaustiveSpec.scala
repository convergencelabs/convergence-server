package com.convergencelabs.server.domain.model.ot

class StringSetSetExhaustiveSpec extends StringOperationExhaustiveSpec[StringSetOperation, StringSetOperation] {

  val serverOperationType: String = "StringSetOperation"
  val clientOperationType: String = "StringSetOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[StringSetOperation, StringSetOperation]] = {
    List(TransformationCase(
      StringSetOperation(valueId, false, "ServerString"),
      StringSetOperation(valueId, false, "ClientString")))
  }

  def transform(s: StringSetOperation, c: StringSetOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSetSetTF.transform(s, c)
  }
}
