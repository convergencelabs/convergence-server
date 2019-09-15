package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayReplaceInsertTF extends OperationTransformationFunction[ArrayReplaceOperation, ArrayInsertOperation] {
  def transform(s: ArrayReplaceOperation, c: ArrayInsertOperation): (ArrayReplaceOperation, ArrayInsertOperation) = {
    if (s.index < c.index) {
      // A-PI-1
      (s, c)
    } else {
      // A-PI-2 and A-PI-3
      (s.copy(index = s.index + 1), c)
    }
  }
}
