package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayMoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArrayMoveSetTF extends OperationTransformationFunction[ArrayMoveOperation, ArraySetOperation] {
  def transform(s: ArrayMoveOperation, c: ArraySetOperation): (ArrayMoveOperation, ArraySetOperation) = {
    (s.copy(noOp = true), c)
  }
}