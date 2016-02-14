package com.convergencelabs.server.domain.model.ot

private[ot] object ArrayRemoveSetTF extends OperationTransformationFunction[ArrayRemoveOperation, ArraySetOperation] {
  def transform(s: ArrayRemoveOperation, c: ArraySetOperation): (ArrayRemoveOperation, ArraySetOperation) = {
    // A-RS-1
    (s.copy(noOp = true), c)
  }
}
