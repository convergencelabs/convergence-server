package com.convergencelabs.server.domain.model.ot.ops

import com.convergencelabs.server.domain.model.ot.xform.StringOperationTransformer

sealed trait StringOperation {
  def transform(other: StringOperation): (DiscreteOperation, DiscreteOperation)
}

case class StringDeleteOperation(override val path: List[Any], override val noOp: Boolean, index: Int, value: String) extends DiscreteOperation(path, noOp) with StringOperation {

  def copyBuilder(): StringDeleteOperation.Builder = new StringDeleteOperation.Builder(path, noOp, index, value)

  def transform(other: StringOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: StringInsertOperation => StringOperationTransformer.transformDeleteInsert(this, other)
    case other: StringDeleteOperation => StringOperationTransformer.transformDeleteDelete(this, other)
    case other: StringSetOperation    => StringOperationTransformer.transformDeleteSet(this, other)
  }
}

object StringDeleteOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int, var value: String) extends DiscreteOperation.Builder(path, noOp) {
    def build(): StringDeleteOperation = StringDeleteOperation(path, noOp, index, value)
  }
}

case class StringInsertOperation(override val path: List[Any], override val noOp: Boolean, index: Int, value: String) extends DiscreteOperation(path, noOp) with StringOperation {

  def copyBuilder(): StringInsertOperation.Builder = new StringInsertOperation.Builder(path, noOp, index, value)

  def transform(other: StringOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: StringInsertOperation => StringOperationTransformer.transformInsertInsert(this, other)
    case other: StringDeleteOperation => StringOperationTransformer.transformInsertDelete(this, other)
    case other: StringSetOperation    => StringOperationTransformer.transformInsertSet(this, other)
  }
}

object StringInsertOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int, var value: String) extends DiscreteOperation.Builder(path, noOp) {
    def build(): StringInsertOperation = StringInsertOperation(path, noOp, index, value)
  }
}

case class StringSetOperation(override val path: List[Any], override val noOp: Boolean, value: String) extends DiscreteOperation(path, noOp) with StringOperation {

  def copyBuilder(): StringSetOperation.Builder = new StringSetOperation.Builder(path, noOp, value)

  def transform(other: StringOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: StringInsertOperation => StringOperationTransformer.transformSetInsert(this, other)
    case other: StringDeleteOperation => StringOperationTransformer.transformSetDelete(this, other)
    case other: StringSetOperation    => StringOperationTransformer.transformSetSet(this, other)
  }
}

object StringSetOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var value: String) extends DiscreteOperation.Builder(path, noOp) {
    def build(): StringSetOperation = StringSetOperation(path, noOp, value)
  }
}
