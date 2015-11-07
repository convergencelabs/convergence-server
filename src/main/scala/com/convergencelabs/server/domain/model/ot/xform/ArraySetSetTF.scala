package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArraySetSetTF extends OperationTransformationFunction[ArraySetOperation, ArraySetOperation] {
  def transform(s: ArraySetOperation, c: ArraySetOperation): (ArraySetOperation, ArraySetOperation) = {
    (s, c.copy(noOp = true))
  }
}