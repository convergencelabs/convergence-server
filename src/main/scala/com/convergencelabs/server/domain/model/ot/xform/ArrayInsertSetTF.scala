package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArrayInsertSetTF extends OperationTransformationFunction[ArrayInsertOperation, ArraySetOperation] {
  def transform(s: ArrayInsertOperation, c: ArraySetOperation): (ArrayInsertOperation, ArraySetOperation) = {
    (s.copy(noOp = true), c)
  }
}