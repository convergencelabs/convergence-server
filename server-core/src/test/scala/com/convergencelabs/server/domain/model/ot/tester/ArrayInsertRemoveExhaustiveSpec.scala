package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayInsertRemoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayRemoveOperation] {

  val serverOperationType: String = "ArrayInsertOperation"
  val clientOperationType: String = "ArrayRemoveOperation"

  val valueId = "testId"
  
  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayRemoveOperation]] = {
    val indices = generateIndices()
    for { i1 <- indices; i2 <- indices } yield TransformationCase(
      ArrayInsertOperation(valueId, false, i1, JString("Y")),
      ArrayRemoveOperation(valueId, false, i2))
  }

  def transform(s: ArrayInsertOperation, c: ArrayRemoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertRemoveTF.transform(s, c)
  }
}
