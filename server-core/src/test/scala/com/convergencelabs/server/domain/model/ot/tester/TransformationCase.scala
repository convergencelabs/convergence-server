package com.convergencelabs.server.domain.model.ot

object TransformationCase {
  def apply[S <: DiscreteOperation, C <: DiscreteOperation](serverOp: S, clientOp: C): TransformationCase[S, C] = new TransformationCase(serverOp, clientOp)
}

class TransformationCase[S <: DiscreteOperation, C <: DiscreteOperation](val serverOp: S, val clientOp: C)
