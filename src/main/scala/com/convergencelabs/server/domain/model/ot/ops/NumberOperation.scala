package com.convergencelabs.server.domain.model.ot.ops

import com.convergencelabs.server.domain.model.ot.xform.NumberOperationTransformer
import org.json4s.JsonAST.JNumber

sealed trait NumberOperation extends DiscreteOperation {
  def transform(other: NumberOperation): (DiscreteOperation, DiscreteOperation)
}

case class NumberAddOperation(path: List[Any], noOp: Boolean, value: JNumber) extends NumberOperation {

  def copyBuilder(): NumberAddOperation.Builder = new NumberAddOperation.Builder(path, noOp, value)

  def transform(other: NumberOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: NumberAddOperation => NumberOperationTransformer.transformAddAdd(this, other)
    case other: NumberSetOperation => NumberOperationTransformer.transformAddSet(this, other)
  }
}

object NumberAddOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var value: JNumber) extends DiscreteOperation.Builder(path, noOp) {
    def build(): NumberAddOperation = NumberAddOperation(path, noOp, value)
  }
}

case class NumberSetOperation(path: List[Any], noOp: Boolean, newValue: JNumber) extends NumberOperation {

  def copyBuilder(): NumberSetOperation.Builder = new NumberSetOperation.Builder(path, noOp, newValue)

  def transform(other: NumberOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: NumberAddOperation => NumberOperationTransformer.transformSetAdd(this, other)
    case other: NumberSetOperation => NumberOperationTransformer.transformSetSet(this, other)
  }
}

object NumberSetOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, newValue: JNumber) extends DiscreteOperation.Builder(path, noOp) {
    def build(): NumberSetOperation = NumberSetOperation(path, noOp, newValue)
  }
}