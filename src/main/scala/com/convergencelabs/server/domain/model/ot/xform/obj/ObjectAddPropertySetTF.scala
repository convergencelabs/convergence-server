package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectAddPropertySetTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectSetOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectSetOperation): (ObjectAddPropertyOperation, ObjectSetOperation) = {
    // O-AS-1
    (s.copy(noOp = true), c)
  }
}
