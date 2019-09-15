package com.convergencelabs.server.domain.model.ot

private[ot] object BooleanSetSetTF extends OperationTransformationFunction[BooleanSetOperation, BooleanSetOperation] {
  def transform(s: BooleanSetOperation, c: BooleanSetOperation): (BooleanSetOperation, BooleanSetOperation) = {
    if (s.value != c.value) {
      // B-SS-1
      (s, c.copy(noOp = true))
    } else {
      // B-SS-2
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
