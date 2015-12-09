package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayInsertSetTF extends OperationTransformationFunction[ArrayInsertOperation, ArraySetOperation] {
  def transform(s: ArrayInsertOperation, c: ArraySetOperation): (ArrayInsertOperation, ArraySetOperation) = {
    (s.copy(noOp = true), c)
  }
}
