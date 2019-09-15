package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayReplaceRemoveTF extends OperationTransformationFunction[ArrayReplaceOperation, ArrayRemoveOperation] {
  def transform(s: ArrayReplaceOperation, c: ArrayRemoveOperation): (ArrayOperation, ArrayRemoveOperation) = {
    if (s.index < c.index) {
      // A-PR-1
      (s, c)
    } else if (s.index == c.index) {
      // A-PR-2
      val ArrayReplaceOperation(path, noOp, index, value) = s
      (ArrayInsertOperation(path, noOp, index, value), c.copy(noOp = true))
    } else {
      // A-PR-3
      (s.copy(index = s.index - 1), c)
    }
  }
}
