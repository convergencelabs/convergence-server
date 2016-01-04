package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetSetTF extends OperationTransformationFunction[ArraySetOperation, ArraySetOperation] {
  def transform(s: ArraySetOperation, c: ArraySetOperation): (ArraySetOperation, ArraySetOperation) = {
    if (s.newValue != c.newValue) {
      // A-SS-1
      (s, c.copy(noOp = true))
    } else {
      // A-SS-2
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
