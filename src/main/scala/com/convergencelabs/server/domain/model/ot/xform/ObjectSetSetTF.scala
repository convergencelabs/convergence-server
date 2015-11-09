package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation

object ObjectSetSetTF extends OperationTransformationFunction[ObjectSetOperation, ObjectSetOperation] {
  def transform(s: ObjectSetOperation, c: ObjectSetOperation): (ObjectSetOperation, ObjectSetOperation) = {
    if (s.newValue == c.newValue) {
      (s.copy(noOp = true), c.copy(noOp = true))
    } else {
      (s, c.copy(noOp = true))
    }
  }
}