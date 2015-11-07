package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArraySetRemoveTF extends OperationTransformationFunction[ArraySetOperation, ArrayRemoveOperation] {
  def transform(s: ArraySetOperation, c: ArrayRemoveOperation): (ArraySetOperation, ArrayRemoveOperation) = {
    (s, c.copy(noOp = true))
  }
}