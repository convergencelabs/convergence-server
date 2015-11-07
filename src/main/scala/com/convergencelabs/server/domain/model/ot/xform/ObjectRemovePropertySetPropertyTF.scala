package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectRemovePropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation

object ObjectRemovePropertySetTF extends OperationTransformationFunction[ObjectRemovePropertyOperation, ObjectSetOperation] {
  def transform(s: ObjectRemovePropertyOperation, c: ObjectSetOperation): (ObjectRemovePropertyOperation, ObjectSetOperation) = {
    (s.copy(noOp = true), c)
  }
}