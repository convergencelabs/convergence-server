package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation

object ObjectAddPropertySetTF extends OperationTransformationFunction[ObjectAddPropertyOperation, ObjectSetOperation] {
  def transform(s: ObjectAddPropertyOperation, c: ObjectSetOperation): (ObjectAddPropertyOperation, ObjectSetOperation) = {
    (s.copy(noOp = true), c)
  }
}