package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayReplaceSetTF extends OperationTransformationFunction[ArrayReplaceOperation, ArraySetOperation] {
  def transform(s: ArrayReplaceOperation, c: ArraySetOperation): (ArrayReplaceOperation, ArraySetOperation) = {
    (s.copy(noOp = true), c)
  }
}