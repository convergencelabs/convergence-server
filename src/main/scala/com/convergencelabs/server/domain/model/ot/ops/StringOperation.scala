package com.convergencelabs.server.domain.model.ot.ops

import com.convergencelabs.server.domain.model.ot.xform.StringOperationTransformer

sealed trait StringOperation extends DiscreteOperation {
  def transform(other: StringOperation): (DiscreteOperation, DiscreteOperation)
}

case class StringRemoveOperation(path: List[Any], noOp: Boolean, index: Int, value: String) extends StringOperation {

  def copyBuilder(): StringRemoveOperation.Builder = new StringRemoveOperation.Builder(path, noOp, index, value)

  def transform(other: StringOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: StringInsertOperation => StringOperationTransformer.transformRemoveInsert(this, other)
    case other: StringRemoveOperation => StringOperationTransformer.transformRemoveRemove(this, other)
    case other: StringSetOperation    => StringOperationTransformer.transformRemoveSet(this, other)
  }
}

object StringRemoveOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int, var value: String) extends DiscreteOperation.Builder(path, noOp) {
    def build(): StringRemoveOperation = StringRemoveOperation(path, noOp, index, value)
  }
}

case class StringInsertOperation(path: List[Any], noOp: Boolean, index: Int, value: String) extends StringOperation {

  def copyBuilder(): StringInsertOperation.Builder = new StringInsertOperation.Builder(path, noOp, index, value)

  def transform(other: StringOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: StringInsertOperation => StringOperationTransformer.transformInsertInsert(this, other)
    case other: StringRemoveOperation => StringOperationTransformer.transformInsertRemove(this, other)
    case other: StringSetOperation    => StringOperationTransformer.transformInsertSet(this, other)
  }
}

object StringInsertOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int, var value: String) extends DiscreteOperation.Builder(path, noOp) {
    def build(): StringInsertOperation = StringInsertOperation(path, noOp, index, value)
  }
}

case class StringSetOperation(path: List[Any], noOp: Boolean, value: String) extends StringOperation {

  def copyBuilder(): StringSetOperation.Builder = new StringSetOperation.Builder(path, noOp, value)

  def transform(other: StringOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: StringInsertOperation => StringOperationTransformer.transformSetInsert(this, other)
    case other: StringRemoveOperation => StringOperationTransformer.transformSetRemove(this, other)
    case other: StringSetOperation    => StringOperationTransformer.transformSetSet(this, other)
  }
}

object StringSetOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var value: String) extends DiscreteOperation.Builder(path, noOp) {
    def build(): StringSetOperation = StringSetOperation(path, noOp, value)
  }
}
