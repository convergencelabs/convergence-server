package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation

object ArrayRemoveRemoveTF extends OperationTransformationFunction[ArrayRemoveOperation, ArrayRemoveOperation] {
  def transform(s: ArrayRemoveOperation, c: ArrayRemoveOperation): (ArrayRemoveOperation, ArrayRemoveOperation) = {
    if (s.index == c.index) {
      (s.copy(noOp = true), c.copy(noOp = true))
    } else if (s.index < c.index) {
      (s, c.copy(index = c.index - 1))
    } else {
      (s.copy(index = s.index - 1), c)
    }
  }
}