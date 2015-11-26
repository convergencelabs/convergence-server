package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayInsertReplaceTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayReplaceOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayReplaceOperation): (ArrayInsertOperation, ArrayReplaceOperation) = {
    if (s.index <= c.index) {
      (s, c.copy(index = c.index + 1))
    } else {
      (s, c)
    }
  }
}