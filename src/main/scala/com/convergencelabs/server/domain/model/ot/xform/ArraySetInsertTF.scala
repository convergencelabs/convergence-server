package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.ArrayInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.ArrayReplaceOperation
import com.convergencelabs.server.domain.model.ot.ops.ArraySetOperation

object ArraySetInsertTF extends OperationTransformationFunction[ArraySetOperation, ArrayInsertOperation] {
  def transform(s: ArraySetOperation, c: ArrayInsertOperation): (ArraySetOperation, ArrayInsertOperation) = {
    (s, c.copy(noOp = true))
  }
}