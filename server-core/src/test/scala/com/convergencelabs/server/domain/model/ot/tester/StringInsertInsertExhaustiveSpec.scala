package com.convergencelabs.server.domain.model.ot

class StringInsertInsertExhaustiveSpec extends StringOperationExhaustiveSpec[StringInsertOperation, StringInsertOperation] {
  
  val serverOperationType: String = "StringInsertOperation"
  val clientOperationType: String = "StringInsertOperation"

  def generateCases(): List[TransformationCase[StringInsertOperation, StringInsertOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      StringInsertOperation(List(), false, i1, "Y"),
      StringInsertOperation(List(), false, i2, "X"))
  }
  
  def transform(s: StringInsertOperation, c: StringInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    StringInsertInsertTF.transform(s, c)
  }
}
