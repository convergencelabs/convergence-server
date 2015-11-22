package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectRemovePropertyRemovePropertyTF extends OperationTransformationFunction[ObjectRemovePropertyOperation, ObjectRemovePropertyOperation] {
  def transform(s: ObjectRemovePropertyOperation, c: ObjectRemovePropertyOperation): (ObjectRemovePropertyOperation, ObjectRemovePropertyOperation) = {
    if (s.property == c.property) {
      (s.copy(noOp = true), c.copy(noOp = true))
    } else {
      (s, c)
    }
  }
}