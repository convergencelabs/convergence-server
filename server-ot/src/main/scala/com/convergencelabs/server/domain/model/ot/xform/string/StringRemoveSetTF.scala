package com.convergencelabs.server.domain.model.ot

private[ot] object StringRemoveSetTF extends OperationTransformationFunction[StringRemoveOperation, StringSetOperation] {
  def transform(s: StringRemoveOperation, c: StringSetOperation): (StringOperation, StringOperation) = {
    // S-RS-1
    (s.copy(noOp = true), c)
  }
}
