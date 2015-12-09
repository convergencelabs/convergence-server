package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayReplaceReplaceTF extends OperationTransformationFunction[ArrayReplaceOperation, ArrayReplaceOperation] {
  def transform(s: ArrayReplaceOperation, c: ArrayReplaceOperation): (ArrayReplaceOperation, ArrayReplaceOperation) = {
    if (s.index == c.index) {
      if (s.value == c.value) {
        (s.copy(noOp = true), c.copy(noOp = true))
      } else {
        (s, c.copy(noOp = true))
      }
    } else {
      (s, c)
    }
  }
}
