package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation

object ObjectSetPropertyRemovePropertyTF extends OperationTransformationFunction[ObjectSetPropertyOperation, ObjectRemovePropertyOperation] {
  def transform(s: ObjectSetPropertyOperation, c: ObjectRemovePropertyOperation): (ObjectOperation, ObjectRemovePropertyOperation) = {
    if (s.property == c.property) {
      val ObjectSetPropertyOperation(path, noOp, prop, value) = s
      (ObjectAddPropertyOperation(path, noOp, prop, value), c.copy(noOp = true))
    } else {
      (s, c)
    }
  }
}