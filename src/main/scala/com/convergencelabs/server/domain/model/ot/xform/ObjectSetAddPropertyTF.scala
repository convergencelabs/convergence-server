package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ObjectSetOperation
import com.convergencelabs.server.domain.model.ot.ops.ObjectAddPropertyOperation

object ObjectSetAddPropertyTF extends OperationTransformationFunction[ObjectSetOperation, ObjectAddPropertyOperation] {
  def transform(s: ObjectSetOperation, c: ObjectAddPropertyOperation): (ObjectSetOperation, ObjectAddPropertyOperation) = {
    (s, c.copy(noOp = true))
  }
}