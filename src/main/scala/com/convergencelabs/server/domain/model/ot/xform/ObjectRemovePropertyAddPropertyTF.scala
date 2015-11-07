package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation

object ObjectRemovePropertySetPropertyTF extends OperationTransformationFunction[ObjectRemovePropertyOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectRemovePropertyOperation, c: ObjectSetPropertyOperation): (ObjectRemovePropertyOperation, ObjectOperation) = {
    if (s.property == c.property) {
      val ObjectSetPropertyOperation(path, noOp, prop, value) = c
      (s.copy(noOp = true), ObjectAddPropertyOperation(path, noOp, prop, value))
    } else {
      (s, c)
    }
  }
}