package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayReplaceInsertTF extends OperationTransformationFunction[ArrayReplaceOperation, ArrayInsertOperation] {
  def transform(s: ArrayReplaceOperation, c: ArrayInsertOperation): (ArrayReplaceOperation, ArrayInsertOperation) = {
    if (c.index <= s.index) {
      (s.copy(index = s.index + 1), c)
    } else {
      (s, c)
    }
  }
}