package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetMoveTF extends OperationTransformationFunction[ArraySetOperation, ArrayMoveOperation] {
  def transform(s: ArraySetOperation, c: ArrayMoveOperation): (ArraySetOperation, ArrayMoveOperation) = {
    (s, c.copy(noOp = true))
  }
}