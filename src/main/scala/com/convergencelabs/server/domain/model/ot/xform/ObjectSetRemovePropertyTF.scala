package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation

object ObjectSetRemovePropertyTF extends OperationTransformationFunction[ObjectSetOperation, ObjectRemovePropertyOperation] {
  def transform(s: ObjectSetOperation, c: ObjectRemovePropertyOperation): (ObjectSetOperation, ObjectRemovePropertyOperation) = {
    (s, c.copy(noOp = true))
  }
}