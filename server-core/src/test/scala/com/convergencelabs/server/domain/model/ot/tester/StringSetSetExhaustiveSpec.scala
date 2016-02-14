package com.convergencelabs.server.domain.model.ot

class StringSetSetExhaustiveSpec extends StringOperationExhaustiveSpec[StringSetOperation, StringSetOperation] {

  val serverOperationType: String = "StringSetOperation"
  val clientOperationType: String = "StringSetOperation"

  def generateCases(): List[TransformationCase[StringSetOperation, StringSetOperation]] = {
    List(TransformationCase(
      StringSetOperation(List(), false, "ServerString"),
      StringSetOperation(List(), false, "ClientString")))
  }

  def transform(s: StringSetOperation, c: StringSetOperation): (DiscreteOperation, DiscreteOperation) = {
    StringSetSetTF.transform(s, c)
  }
}
