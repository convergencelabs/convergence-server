package com.convergencelabs.server.domain.model.ot

private[ot] object NumberAddSetTF extends OperationTransformationFunction[NumberAddOperation, NumberSetOperation] {
  def transform(s: NumberAddOperation, c: NumberSetOperation): (NumberAddOperation, NumberSetOperation) = {
    // N-AS-1
    (s.copy(noOp = true), c)
  }
}
