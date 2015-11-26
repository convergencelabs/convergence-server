package com.convergencelabs.server.domain.model.ot

private[ot] object NumberSetSetTF extends OperationTransformationFunction[NumberSetOperation, NumberSetOperation] {
  def transform(s: NumberSetOperation, c: NumberSetOperation): (NumberSetOperation, NumberSetOperation) = {
    if (s.newValue == c.newValue) {
      (s.copy(noOp = true), c.copy(noOp = true))
    } else {
      (s, c.copy(noOp = true))
    }
  }
}