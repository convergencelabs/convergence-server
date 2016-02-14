package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayMoveInsertExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayMoveOperation, ArrayInsertOperation] {

  val serverOperationType: String = "ArrayMoveOperation"
  val clientOperationType: String = "ArrayInsertOperation"

  def generateCases(): List[TransformationCase[ArrayMoveOperation, ArrayInsertOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayMoveOperation(List(), false, r.fromIndex, r.toIndex),
      ArrayInsertOperation(List(), false, i, JString("X")))
  }

  def transform(s: ArrayMoveOperation, c: ArrayInsertOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayMoveInsertTF.transform(s, c)
  }
}
