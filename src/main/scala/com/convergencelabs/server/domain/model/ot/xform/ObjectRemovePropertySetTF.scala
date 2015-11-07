package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation

object ObjectRemovePropertyRemovePropertyTF extends OperationTransformationFunction[ObjectRemovePropertyOperation, ObjectRemovePropertyOperation] {
  def transform(s: ObjectRemovePropertyOperation, c: ObjectRemovePropertyOperation): (ObjectRemovePropertyOperation, ObjectRemovePropertyOperation) = {
    if (s.property == c.property) {
      (s.copy(noOp = true), c.copy(noOp = true))
    } else {
      (s, c)
    }
  }
}