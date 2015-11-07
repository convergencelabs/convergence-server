package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.ops.NumberSetOperation

object NumberAddSetTF extends OperationTransformationFunction[NumberAddOperation, NumberSetOperation] {
  def transform(s: NumberAddOperation, c: NumberSetOperation): (NumberAddOperation, NumberSetOperation) = {
    (s.copy(noOp = true), c)
  }
}