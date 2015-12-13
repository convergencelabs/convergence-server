package com.convergencelabs.server.domain.model.ot

private[ot] object BooleanSetSetTF extends OperationTransformationFunction[BooleanSetOperation, BooleanSetOperation] {
  def transform(s: BooleanSetOperation, c: BooleanSetOperation): (BooleanSetOperation, BooleanSetOperation) = {
    if (s.value == c.value) {
      (s.copy(noOp = true), c.copy(noOp = true))
    } else {
      (s, c.copy(noOp = true))
    }
  }
}
