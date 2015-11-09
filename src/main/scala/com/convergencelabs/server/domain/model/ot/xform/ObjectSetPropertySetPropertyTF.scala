package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation

object ObjectSetPropertySetPropertyTF extends OperationTransformationFunction[ObjectSetPropertyOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectSetPropertyOperation, c: ObjectSetPropertyOperation): (ObjectSetPropertyOperation, ObjectSetPropertyOperation) = {
    if (s.property == c.property) {
      if (s.newValue == c.newValue) {
        (s.copy(noOp = true), c.copy(noOp = true))
      } else {
        (s, c.copy(noOp = true))
      }
    } else {
      (s, c)
    }
  }
}
