package com.convergencelabs.server.domain.model.ot

private[ot] object StringInsertSetTF extends OperationTransformationFunction[StringInsertOperation, StringSetOperation] {
  def transform(s: StringInsertOperation, c: StringSetOperation): (StringOperation, StringOperation) = {
    // S-IS-1
    (s.copy(noOp = true), c)
  }
}
