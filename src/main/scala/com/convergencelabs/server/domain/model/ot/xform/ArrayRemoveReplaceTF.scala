package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayOperation

object ArrayRemoveReplaceTF extends OperationTransformationFunction[ArrayRemoveOperation, ArrayReplaceOperation] {
  def transform(s: ArrayRemoveOperation, c: ArrayReplaceOperation): (ArrayRemoveOperation, ArrayOperation) = {
    if (s.index < c.index) {
      // The remove is strictly less than the replace.
      // Decrement the replace.
      (s, c.copy(index = c.index - 1))
    } else if (s.index == c.index) {
      // The remove index is equal to the replace index.
      // noOp the remove and turn the replace into an insert.
      val ArrayReplaceOperation(path, noOp, index, newValue) = c
      (s.copy(noOp = true), ArrayInsertOperation(path, noOp, index, newValue))
    }  else {
      // The remove is after the replace.  No transformation.
      (s, c)
    }
  }
}