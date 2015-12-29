package com.convergencelabs.server.domain.model.ot

private[ot] object StringSetInsertTF extends OperationTransformationFunction[StringSetOperation, StringInsertOperation] {
  def transform(s: StringSetOperation, c: StringInsertOperation): (StringOperation, StringOperation) = {
    // S-SI-1
    (s, c.copy(noOp = true))
  }
}
