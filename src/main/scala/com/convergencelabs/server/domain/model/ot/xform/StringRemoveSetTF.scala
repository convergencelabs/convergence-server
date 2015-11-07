package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.StringOperation
import com.convergencelabs.server.domain.model.ot.ops.StringRemoveOperation
import com.convergencelabs.server.domain.model.ot.ops.StringSetOperation

object StringRemoveSetTF extends OperationTransformationFunction[StringRemoveOperation, StringSetOperation] {
  def transform(s: StringRemoveOperation, c: StringSetOperation): (StringOperation, StringOperation) = {
    (s.copy(noOp = true), c)
  }
}