package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation

object ObjectSetSetPropertyTF extends OperationTransformationFunction[ObjectSetOperation, ObjectSetPropertyOperation] {
  def transform(s: ObjectSetOperation, c: ObjectSetPropertyOperation): (ObjectSetOperation, ObjectSetPropertyOperation) = {
    (s, c.copy(noOp = true))
  }
}