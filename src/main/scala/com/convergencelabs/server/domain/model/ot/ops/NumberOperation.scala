package com.convergencelabs.server.domain.model.ot.ops

import com.convergencelabs.server.domain.model.ot.xform.NumberOperationTransformer

sealed trait NumberOperation {
  def transform(other: NumberOperation): (DiscreteOperation, DiscreteOperation)
}

case class NumberAddOperation(override val path: List[Any], override val noOp: Boolean, value: BigDecimal) extends DiscreteOperation(path, noOp) with NumberOperation {

  def invert(): DiscreteOperation = NumberAddOperation(path, noOp, -value)

  def copyBuilder(): NumberAddOperation.Builder = new NumberAddOperation.Builder(path, noOp, value)

  def transform(other: NumberOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: NumberAddOperation => NumberOperationTransformer.transformAddAdd(this, other)
    case other: NumberSetOperation => NumberOperationTransformer.transformAddSet(this, other)
  }
}

object NumberAddOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var value: BigDecimal) extends DiscreteOperation.Builder(path, noOp) {
    def build(): NumberAddOperation = NumberAddOperation(path, noOp, value)
  }
}

case class NumberSetOperation(override val path: List[Any], override val noOp: Boolean, oldValue: BigDecimal, newValue: BigDecimal) extends DiscreteOperation(path, noOp) with NumberOperation {

  def invert(): DiscreteOperation = NumberSetOperation(path, noOp, newValue, oldValue)

  def copyBuilder(): NumberSetOperation.Builder = new NumberSetOperation.Builder(path, noOp, oldValue, newValue)

  def transform(other: NumberOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: NumberAddOperation => NumberOperationTransformer.transformSetAdd(this, other)
    case other: NumberSetOperation => NumberOperationTransformer.transformSetSet(this, other)
  }
}

object NumberSetOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var oldValue: BigDecimal, newValue: BigDecimal) extends DiscreteOperation.Builder(path, noOp) {
    def build(): NumberSetOperation = NumberSetOperation(path, noOp, oldValue, newValue)
  }
}