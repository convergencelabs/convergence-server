package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.StringOperation
import com.convergencelabs.server.domain.model.ot.ops.StringInsertOperation

object StringInsertInsertTF extends OperationTransformationFunction[StringInsertOperation, StringInsertOperation] {
  def transform(s: StringInsertOperation, u: StringInsertOperation): (StringOperation, StringOperation) = {
    if (s.index <= u.index) {
      (s, u.copy(index = u.index + s.value.length))
    } else {
      (s.copy(index = s.index + u.value.length), u)
    }
  }
}