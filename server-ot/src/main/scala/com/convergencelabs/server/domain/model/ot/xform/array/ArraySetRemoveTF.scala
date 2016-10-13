package com.convergencelabs.server.domain.model.ot

private[ot] object ArraySetRemoveTF extends OperationTransformationFunction[ArraySetOperation, ArrayRemoveOperation] {
  def transform(s: ArraySetOperation, c: ArrayRemoveOperation): (ArraySetOperation, ArrayRemoveOperation) = {
    // A-SR-1
    (s, c.copy(noOp = true))
  }
}
