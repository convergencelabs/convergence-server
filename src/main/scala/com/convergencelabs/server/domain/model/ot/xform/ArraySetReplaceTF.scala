package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArraySetReplaceTF extends OperationTransformationFunction[ArraySetOperation, ArrayReplaceOperation] {
  def transform(s: ArraySetOperation, c: ArrayReplaceOperation): (ArraySetOperation, ArrayReplaceOperation) = {
    (s, c.copy(noOp = true))
  }
}