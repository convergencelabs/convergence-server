package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetSetPropertyTF extends OperationTransformationFunction[ObjectSetOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectSetOperation, c: ObjectSetPropertyOperation): (ObjectSetOperation, ObjectSetPropertyOperation) = {
    (s, c.copy(noOp = true))
  }
}
