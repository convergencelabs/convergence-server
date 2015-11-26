package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectRemovePropertySetPropertyTF extends OperationTransformationFunction[ObjectRemovePropertyOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectRemovePropertyOperation, c: ObjectSetPropertyOperation): (ObjectRemovePropertyOperation, ObjectOperation) = {
    if (s.property == c.property) {
      val ObjectSetPropertyOperation(path, noOp, prop, value) = c
      (s.copy(noOp = true), ObjectAddPropertyOperation(path, noOp, prop, value))
    } else {
      (s, c)
    }
  }
}