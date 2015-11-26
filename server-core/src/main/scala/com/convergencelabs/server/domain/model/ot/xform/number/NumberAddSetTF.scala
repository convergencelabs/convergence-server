package com.convergencelabs.server.domain.model.ot

private[ot] object NumberAddSetTF extends OperationTransformationFunction[NumberAddOperation, NumberSetOperation] {
  def transform(s: NumberAddOperation, c: NumberSetOperation): (NumberAddOperation, NumberSetOperation) = {
    (s.copy(noOp = true), c)
  }
}