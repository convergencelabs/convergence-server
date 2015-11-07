package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectOperation

object ObjectAddPropertyAddPropertyTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectAddPropertyOperation): (ObjectAddPropertyOperation, ObjectOperation) = {
    if (s.property == c.property) {
      val ObjectAddPropertyOperation(path, noOp, prop, value) = c
      (s, ObjectSetPropertyOperation(path, noOp, prop, value))
    } else {
      (s, c)
    }
  }
}