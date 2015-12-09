package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectAddPropertySetTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectSetOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectSetOperation): (ObjectAddPropertyOperation, ObjectSetOperation) = {
    (s.copy(noOp = true), c)
  }
}
