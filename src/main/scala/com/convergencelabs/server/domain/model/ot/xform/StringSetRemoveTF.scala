package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.StringOperation
import com.convergencelabs.server.domain.model.ot.ops.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.StringSetOperation

object StringSetRemoveTF extends OperationTransformationFunction[StringSetOperation, StringRemoveOperation] {
  def transform(s: StringSetOperation, c: StringRemoveOperation): (StringOperation, StringOperation) = {
    (s, c.copy(noOp = true))
  }
}