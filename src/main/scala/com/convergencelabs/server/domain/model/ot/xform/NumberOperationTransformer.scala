package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.NumberAddOperation
import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation
import com.convergencelabs.server.domain.model.ot.ops.NumberSetOperation

object NumberOperationTransformer {
  def transformAddAdd(op1: NumberAddOperation, op2: NumberAddOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformAddSet(op1: NumberAddOperation, op2: NumberSetOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetAdd(op1: NumberSetOperation, op2: NumberAddOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }

  def transformSetSet(op1: NumberSetOperation, op2: NumberSetOperation): (DiscreteOperation, DiscreteOperation) = {
    (null, null)
  }
}