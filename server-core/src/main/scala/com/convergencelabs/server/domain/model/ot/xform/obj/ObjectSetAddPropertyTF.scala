package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetAddPropertyTF extends OperationTransformationFunction[ObjectSetOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectSetOperation, c: ObjectAddPropertyOperation): (ObjectSetOperation, ObjectAddPropertyOperation) = {
    (s, c.copy(noOp = true))
  }
}