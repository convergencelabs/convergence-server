package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.NumberAddOperation

object NumberAddAddTF extends OperationTransformationFunction[NumberAddOperation, NumberAddOperation] {
  def transform(s: NumberAddOperation, c: NumberAddOperation): (NumberAddOperation, NumberAddOperation) = {
    (s, c)
  }
}