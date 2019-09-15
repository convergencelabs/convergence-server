package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetAddPropertyTF extends OperationTransformationFunction[ObjectSetOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectSetOperation, c: ObjectAddPropertyOperation): (ObjectSetOperation, ObjectAddPropertyOperation) = {
    // O-SA-1
    (s, c.copy(noOp = true))
  }
}
