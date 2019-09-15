package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayReplaceSetTF extends OperationTransformationFunction[ArrayReplaceOperation, ArraySetOperation] {
  def transform(s: ArrayReplaceOperation, c: ArraySetOperation): (ArrayReplaceOperation, ArraySetOperation) = {
    // A-PS-1
    (s.copy(noOp = true), c)
  }
}
