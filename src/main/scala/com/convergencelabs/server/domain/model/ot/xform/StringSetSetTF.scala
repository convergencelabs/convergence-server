package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.StringOperation
import com.convergencelabs.server.domain.model.ot.ops.StringSetOperation

object StringSetSetTF extends OperationTransformationFunction[StringSetOperation, StringSetOperation] {
  def transform(s: StringSetOperation, c: StringSetOperation): (StringOperation, StringOperation) = {
    if (s.value == c.value) {
      // FIXME do we do this?
      (s.copy(noOp = true), s.copy(noOp = true))
    } else {
      (s, c.copy(noOp = true))
    }
  }
}