package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetPropertyOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation

object ObjectSetPropertySetTF extends OperationTransformationFunction[ObjectSetPropertyOperation, ObjectSetOperation] {
  def transform(s: ObjectSetPropertyOperation, c: ObjectSetOperation): (ObjectSetPropertyOperation, ObjectSetOperation) = {
      (s.copy(noOp = true), c)
  }
}