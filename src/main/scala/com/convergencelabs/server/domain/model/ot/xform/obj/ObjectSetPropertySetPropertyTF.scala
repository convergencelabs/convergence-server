package com.convergencelabs.server.domain.model.ot

private[ot] object ObjectSetPropertySetPropertyTF extends OperationTransformationFunction[ObjectSetPropertyOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectSetPropertyOperation, c: ObjectSetPropertyOperation): (ObjectSetPropertyOperation, ObjectSetPropertyOperation) = {
    if (s.property != c.property) {
      // O-TT-1
      (s, c)
    } else if (s.value != c.value) {
      // O-TT-2
      (s, c.copy(noOp = true))
    } else {
      // O-TT-3
      (s.copy(noOp = true), c.copy(noOp = true))
    }
  }
}
