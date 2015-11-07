package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation

object ArrayReplaceReplaceTF extends OperationTransformationFunction[ArrayReplaceOperation, ArrayReplaceOperation] {
  def transform(s: ArrayReplaceOperation, c: ArrayReplaceOperation): (ArrayReplaceOperation, ArrayReplaceOperation) = {
    if (s.index == c.index) {
      if (s.newValue == c.newValue) {
        (s.copy(noOp = true), c.copy(noOp = true))
      } else {
        (s, c.copy(noOp = true))
      }
    } else {
      (s, c)
    }
  }
}