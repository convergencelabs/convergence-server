package com.convergencelabs.server.domain.model.ot

private[ot] object NumberSetSetTF extends OperationTransformationFunction[NumberSetOperation, NumberSetOperation] {
  def transform(s: NumberSetOperation, c: NumberSetOperation): (NumberSetOperation, NumberSetOperation) = {
    if (s.value != c.value) {
      // N-SS-1
      (s, c.copy(noOp = true))
    } else {
      // N-SS-2
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
