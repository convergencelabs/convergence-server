package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArrayRemoveSetTF extends OperationTransformationFunction[ArrayRemoveOperation, ArraySetOperation] {
  def transform(s: ArrayRemoveOperation, c: ArraySetOperation): (ArrayRemoveOperation, ArraySetOperation) = {
    (s.copy(noOp = true), c)
  }
}