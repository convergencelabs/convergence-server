package com.convergencelabs.server.domain.model.ot

private[ot] object DateSetSetTF extends OperationTransformationFunction[DateSetOperation, DateSetOperation] {
  def transform(s: DateSetOperation, c: DateSetOperation): (DateOperation, DateOperation) = {
    if (s.value == c.value) {
      // S-SS-1
      (s.copy(noOp = true), s.copy(noOp = true))
    } else {
      // S-SS-2
      (s, c.copy(noOp = true))
    }
  }
}
