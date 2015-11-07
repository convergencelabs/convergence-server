package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.StringOperation
import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.StringSetOperation

object StringInsertSetTF extends OperationTransformationFunction[StringInsertOperation, StringSetOperation] {
  def transform(s: StringInsertOperation, c: StringSetOperation): (StringOperation, StringOperation) = {
    (s.copy(noOp = true), c)
  }
}