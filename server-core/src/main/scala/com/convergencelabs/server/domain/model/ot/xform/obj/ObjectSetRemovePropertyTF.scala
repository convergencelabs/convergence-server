package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetRemovePropertyTF extends OperationTransformationFunction[ObjectSetOperation, ObjectRemovePropertyOperation] {
  def transform(s: ObjectSetOperation, c: ObjectRemovePropertyOperation): (ObjectSetOperation, ObjectRemovePropertyOperation) = {
    (s, c.copy(noOp = true))
  }
}
