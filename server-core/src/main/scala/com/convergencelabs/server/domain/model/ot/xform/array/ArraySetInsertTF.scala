package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetInsertTF extends OperationTransformationFunction[ArraySetOperation, ArrayInsertOperation] {
  def transform(s: ArraySetOperation, c: ArrayInsertOperation): (ArraySetOperation, ArrayInsertOperation) = {
    // A-SI-1
    (s, c.copy(noOp = true))
  }
}
