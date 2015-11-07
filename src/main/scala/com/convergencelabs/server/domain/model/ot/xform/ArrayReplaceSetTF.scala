package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArrayReplaceSetTF extends OperationTransformationFunction[ArrayReplaceOperation, ArraySetOperation] {
  def transform(s: ArrayReplaceOperation, c: ArraySetOperation): (ArrayReplaceOperation, ArraySetOperation) = {
    (s.copy(noOp = true), c)
  }
}