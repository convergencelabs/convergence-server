package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectRemovePropertySetTF extends OperationTransformationFunction[ObjectRemovePropertyOperation, ObjectSetOperation] {
  def transform(s: ObjectRemovePropertyOperation, c: ObjectSetOperation): (ObjectRemovePropertyOperation, ObjectSetOperation) = {
    (s.copy(noOp = true), c)
  }
}