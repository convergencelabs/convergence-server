package com.convergencelabs.server.domain.model.ot

import org.json4s.JString

class ArrayInsertMoveExhaustiveSpec extends ArrayOperationExhaustiveSpec[ArrayInsertOperation, ArrayMoveOperation] {

  val serverOperationType: String = "ArrayInsertOperation"
  val clientOperationType: String = "ArrayMoveOperation"

  def generateCases(): List[TransformationCase[ArrayInsertOperation, ArrayMoveOperation]] = {
    for { r <- generateMoveRanges(); i <- generateIndices() } yield TransformationCase(
      ArrayInsertOperation(List(), false, i, JString("X")),
      ArrayMoveOperation(List(), false, r.fromIndex, r.toIndex))
  }

  def transform(s: ArrayInsertOperation, c: ArrayMoveOperation): (DiscreteOperation, DiscreteOperation) = {
    ArrayInsertMoveTF.transform(s, c)
  }
}
