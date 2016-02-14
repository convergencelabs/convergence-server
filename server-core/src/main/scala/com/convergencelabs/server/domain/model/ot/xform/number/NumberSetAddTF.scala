package com.convergencelabs.server.domain.model.ot

private[ot] object NumberSetAddTF extends OperationTransformationFunction[NumberSetOperation, NumberAddOperation] {
  def transform(s: NumberSetOperation, c: NumberAddOperation): (NumberSetOperation, NumberAddOperation) = {
    // N-SA-1
    (s, c.copy(noOp = true))
  }
}
