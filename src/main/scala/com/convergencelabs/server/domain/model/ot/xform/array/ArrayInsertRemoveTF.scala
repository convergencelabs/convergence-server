package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayInsertRemoveTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayRemoveOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayRemoveOperation): (ArrayInsertOperation, ArrayRemoveOperation) = {
    if (s.index <= c.index) {
      (s, c.copy(index = c.index + 1))
    } else {
      (s.copy(index = s.index - 1), c)
    }
  }
}