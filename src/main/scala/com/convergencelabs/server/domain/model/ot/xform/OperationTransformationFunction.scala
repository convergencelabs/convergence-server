package com.convergencelabs.server.domain.model.ot

private[ot] trait OperationTransformationFunction[S <: DiscreteOperation, C <: DiscreteOperation] {
  def transform(s: S, c: C): (DiscreteOperation, DiscreteOperation)
}