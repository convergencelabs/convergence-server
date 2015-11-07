package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.StringOperation
import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.StringSetOperation

object StringSetInsertTF extends OperationTransformationFunction[StringSetOperation, StringInsertOperation] {
  def transform(s: StringSetOperation, c: StringInsertOperation): (StringOperation, StringOperation) = {
    (s, c.copy(noOp = true))
  }
}