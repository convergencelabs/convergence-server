package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.ops.NumberSetOperation

object NumberSetAddTF extends OperationTransformationFunction[NumberSetOperation, NumberAddOperation] {
  def transform(s: NumberSetOperation, c: NumberAddOperation): (NumberSetOperation, NumberAddOperation) = {
    (s, c.copy(noOp = true))
  }
}