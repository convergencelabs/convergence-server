package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation

object ArrayInsertReplaceTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayReplaceOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayReplaceOperation): (ArrayInsertOperation, ArrayReplaceOperation) = {
    if (s.index <= c.index) {
      (s, c.copy(index = c.index + 1))
    } else {
      (s, c)
    }
  }
}