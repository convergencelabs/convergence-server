package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayOperation

object ArrayReplaceRemoveTF extends OperationTransformationFunction[ArrayReplaceOperation, ArrayRemoveOperation] {
  def transform(s: ArrayReplaceOperation, c: ArrayRemoveOperation): (ArrayOperation, ArrayRemoveOperation) = {
    if (c.index < s.index) {
      // The remove is less than the replace
      // decrement the index of the replace.
      (s.copy(index = s.index - 1), c)
    } else if (s.index == c.index) {
      // The replace and remove are at the same index.
      // opOp the remove, and convert the replace into an insert.
      val ArrayReplaceOperation(path, noOp, index, value) = s
      (ArrayInsertOperation(path, noOp, index, value), c.copy(noOp = true))
    } else {
      // The remove is after the replace, no transformation needed
      (s, c)
    }
  }
}