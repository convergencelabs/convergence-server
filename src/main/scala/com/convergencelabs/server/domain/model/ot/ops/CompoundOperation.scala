package com.convergencelabs.server.domain.model.ot.ops

case class CompoundOperation(val operations: List[DiscreteOperation]) extends Operation {
}