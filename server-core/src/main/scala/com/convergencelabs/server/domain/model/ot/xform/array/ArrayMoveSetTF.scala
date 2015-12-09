package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayMoveSetTF extends OperationTransformationFunction[ArrayMoveOperation, ArraySetOperation] {
  def transform(s: ArrayMoveOperation, c: ArraySetOperation): (ArrayMoveOperation, ArraySetOperation) = {
    (s.copy(noOp = true), c)
  }
}
