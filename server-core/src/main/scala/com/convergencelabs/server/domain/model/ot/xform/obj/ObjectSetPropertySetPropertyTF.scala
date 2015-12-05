package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetPropertySetPropertyTF extends OperationTransformationFunction[ObjectSetPropertyOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectSetPropertyOperation, c: ObjectSetPropertyOperation): (ObjectSetPropertyOperation, ObjectSetPropertyOperation) = {
    if (s.property == c.property) {
      if (s.value == c.value) {
        (s.copy(noOp = true), c.copy(noOp = true))
      } else {
        (s, c.copy(noOp = true))
      }
    } else {
      (s, c)
    }
  }
}
