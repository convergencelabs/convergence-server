package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayMoveSetTF extends OperationTransformationFunction[ArrayMoveOperation, ArraySetOperation] {
  def transform(s: ArrayMoveOperation, c: ArraySetOperation): (ArrayMoveOperation, ArraySetOperation) = {
    // A-MS-1
    (s.copy(noOp = true), c)
  }
}
