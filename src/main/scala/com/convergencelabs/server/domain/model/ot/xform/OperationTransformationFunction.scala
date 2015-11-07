package com.convergencelabs.server.domain.model.ot.xform

import com.convergencelabs.server.domain.model.ot.ops.DiscreteOperation

trait OperationTransformationFunction[S <: DiscreteOperation, C <: DiscreteOperation] {
  def transform(s: S, c: C): (DiscreteOperation, DiscreteOperation)
}