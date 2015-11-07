package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArraySetMoveTF extends OperationTransformationFunction[ArraySetOperation, ArrayMoveOperation] {
  def transform(s: ArraySetOperation, c: ArrayMoveOperation): (ArraySetOperation, ArrayMoveOperation) = {
    (s, c.copy(noOp = true))
  }
}