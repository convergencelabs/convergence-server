package com.convergencelabs.server.domain.model.ot

class ArrayRemoveRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayRemoveOperation, ArrayRemoveOperation] {

  val serverOperationType: String = "ArrayRemoveOperation"
  val clientOperationType: String = "ArrayRemoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayRemoveOperation, ArrayRemoveOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayRemoveOperation(valueId, false, i1),
      ArrayRemoveOperation(valueId, false, i2))
  }

  def transform(s: ArrayRemoveOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayRemoveRemoveTF.transform(s, c)
  }
}
