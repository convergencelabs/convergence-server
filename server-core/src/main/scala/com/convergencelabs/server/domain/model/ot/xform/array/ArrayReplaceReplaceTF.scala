package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayReplaceReplaceTF extends OperationTransformationFunction[ArrayReplaceOperation, ArrayReplaceOperation] {
  def transform(s: ArrayReplaceOperation, c: ArrayReplaceOperation): (ArrayReplaceOperation, ArrayReplaceOperation) = {
    if (s.index != c.index) {
      // A-PP-1
      (s, c)
    } else if (s.value != c.value) {
      // A-PP-2
      (s, c.copy(noOp = true))
    } else {
      // A-PP-3
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
