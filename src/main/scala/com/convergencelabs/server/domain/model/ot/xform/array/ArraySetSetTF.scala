package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetSetTF extends OperationTransformationFunction[ArraySetOperation, ArraySetOperation] {
  def transform(s: ArraySetOperation, c: ArraySetOperation): (ArraySetOperation, ArraySetOperation) = {
    (s, c.copy(noOp = true))
  }
}