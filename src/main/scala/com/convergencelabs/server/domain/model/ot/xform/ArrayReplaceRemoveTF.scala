package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation

object ArrayReplaceRemoveTF extends OperationTransformationFunction[ArrayReplaceOperation, ArrayRemoveOperation] {
  def transform(s: ArrayReplaceOperation, c: ArrayRemoveOperation): (ArrayReplaceOperation, ArrayRemoveOperation) = {
    if (s.index == c.index) {
      (s.copy(noOp = true), c)
    } else if (c.index < s.index) {
      (s, c.copy(index = c.index - 1))
    } else {
      (s, c)
    }
  }
}