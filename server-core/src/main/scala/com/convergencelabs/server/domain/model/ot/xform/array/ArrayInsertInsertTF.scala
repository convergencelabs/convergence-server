package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayInsertInsertTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayInsertOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayInsertOperation): (ArrayInsertOperation, ArrayInsertOperation) = {
    if (s.index <= c.index) {
      (s, c.copy(index = c.index + 1))
    } else {
      (s.copy(index = s.index + 1), c)
    }
  }
}
