package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetReplaceTF extends OperationTransformationFunction[ArraySetOperation, ArrayReplaceOperation] {
  def transform(s: ArraySetOperation, c: ArrayReplaceOperation): (ArraySetOperation, ArrayReplaceOperation) = {
    // A-SP-1
    (s, c.copy(noOp = true))
  }
}
