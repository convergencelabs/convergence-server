package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.StringOperation
import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation
import com.convergencelabs.server.domain.model.ot.ops.StringRemoveOperation

object StringInsertRemoveTF extends OperationTransformationFunction[StringInsertOperation, StringRemoveOperation] {
  def transform(s: StringInsertOperation, c: StringRemoveOperation): (StringOperation, StringOperation) = {
    if (s.index <= c.index) {
      (s, c.copy(index = c.index + s.value.length))
    } else if (c.index + c.value.length <= s.index) {
      (s.copy(index = s.index - c.value.length), c)
    } else {
      val offsetDelta = s.index - c.index
      (s.copy(noOp = true), 
          c.copy(
              value = c.value.substring(0, offsetDelta) + 
              s.value + 
              c.value.substring(offsetDelta, c.value.length)))
    }
  }
}