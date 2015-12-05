package com.convergencelabs.server.domain.model.ot

private[ot] object NumberSetSetTF extends OperationTransformationFunction[NumberSetOperation, NumberSetOperation] {
  def transform(s: NumberSetOperation, c: NumberSetOperation): (NumberSetOperation, NumberSetOperation) = {
    if (s.value == c.value) {
      (s.copy(noOp = true), c.copy(noOp = true))
    } else {
      (s, c.copy(noOp = true))
    }
  }
}