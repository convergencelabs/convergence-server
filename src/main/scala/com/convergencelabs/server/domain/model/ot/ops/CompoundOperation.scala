package com.convergencelabs.server.domain.model.ot.ops

class CompoundOperation(val operations: List[DiscreteOperation]) extends Operation {
  def invert(): Operation = {
    return new CompoundOperation(operations.reverse.map(_.invert()))
  }
}