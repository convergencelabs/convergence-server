package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetReplaceTF extends OperationTransformationFunction[ArraySetOperation, ArrayReplaceOperation] {
  def transform(s: ArraySetOperation, c: ArrayReplaceOperation): (ArraySetOperation, ArrayReplaceOperation) = {
    (s, c.copy(noOp = true))
  }
}
