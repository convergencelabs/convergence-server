package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetPropertyRemovePropertyTF extends OperationTransformationFunction[ObjectSetPropertyOperation, ObjectRemovePropertyOperation] {
  def transform(s: ObjectSetPropertyOperation, c: ObjectRemovePropertyOperation): (ObjectOperation, ObjectRemovePropertyOperation) = {
    if (s.property != c.property) {
      // O-TR-1
      (s, c)
    } else {
      // O-TR-2
      val ObjectSetPropertyOperation(path, noOp, prop, value) = s
      (ObjectAddPropertyOperation(path, noOp, prop, value), c.copy(noOp = true))
    }
  }
}
