package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation

object ArrayInsertRemoveTF extends OperationTransformationFunction[ArrayInsertOperation, ArrayRemoveOperation] {
  def transform(s: ArrayInsertOperation, c: ArrayRemoveOperation): (ArrayInsertOperation, ArrayRemoveOperation) = {
    if (s.index <= c.index) {
      (s, c.copy(index = c.index + 1))
    } else {
      (s.copy(index = s.index - 1), c)
    }
  }
}