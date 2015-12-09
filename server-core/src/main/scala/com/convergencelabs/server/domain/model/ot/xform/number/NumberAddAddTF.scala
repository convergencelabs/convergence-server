package com.convergencelabs.server.domain.model.ot

private[ot] object NumberAddAddTF extends OperationTransformationFunction[NumberAddOperation, NumberAddOperation] {
  def transform(s: NumberAddOperation, c: NumberAddOperation): (NumberAddOperation, NumberAddOperation) = {
    (s, c)
  }
}
