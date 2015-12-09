package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetInsertTF extends OperationTransformationFunction[ArraySetOperation, ArrayInsertOperation] {
  def transform(s: ArraySetOperation, c: ArrayInsertOperation): (ArraySetOperation, ArrayInsertOperation) = {
    (s, c.copy(noOp = true))
  }
}
