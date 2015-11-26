package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayRemoveInsertTF extends OperationTransformationFunction[ArrayRemoveOperation, ArrayInsertOperation] {
  def transform(s: ArrayRemoveOperation, c: ArrayInsertOperation): (ArrayRemoveOperation, ArrayInsertOperation) = {
    if (s.index <= c.index) {
      (s, c.copy(index = c.index - 1))
    } else {
      (s.copy(index = s.index + 1), c)
    }
  }
}