package com.convergencelabs.server.domain.model.ot.ops

import com.fasterxml.jackson.databind.JsonNode
import com.convergencelabs.server.domain.model.ot.xform.ArrayOperationTransformer

sealed trait ArrayOperation {
  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation)
}

case class ArrayInsertOperation(override val path: List[Any], override val noOp: Boolean, index: Int, value: JsonNode) extends DiscreteOperation(path, noOp) with ArrayOperation {

  def invert(): DiscreteOperation = ArrayRemoveOperation(path, noOp, index, value)

  def copyBuilder(): ArrayInsertOperation.Builder = new ArrayInsertOperation.Builder(path, noOp, index, value)

  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ArrayInsertOperation  => ArrayOperationTransformer.transformInsertInsert(this, other)
    case other: ArrayRemoveOperation  => ArrayOperationTransformer.transformInsertRemove(this, other)
    case other: ArrayReplaceOperation => ArrayOperationTransformer.transformInsertReplace(this, other)
    case other: ArrayMoveOperation    => ArrayOperationTransformer.transformInsertMove(this, other)
    case other: ArraySetOperation     => ArrayOperationTransformer.transformInsertSet(this, other)
  }
}

object ArrayInsertOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int, var value: JsonNode) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ArrayInsertOperation = ArrayInsertOperation(path, noOp, index, value)
  }
}

case class ArrayRemoveOperation(override val path: List[Any], override val noOp: Boolean, index: Int, oldValue: JsonNode) extends DiscreteOperation(path, noOp) with ArrayOperation {

  def invert(): DiscreteOperation = ArrayInsertOperation(path, noOp, index, oldValue)

  def copyBuilder(): ArrayRemoveOperation.Builder = new ArrayRemoveOperation.Builder(path, noOp, index, oldValue)

  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ArrayInsertOperation  => ArrayOperationTransformer.transformRemoveInsert(this, other)
    case other: ArrayRemoveOperation  => ArrayOperationTransformer.transformRemoveRemove(this, other)
    case other: ArrayReplaceOperation => ArrayOperationTransformer.transformRemoveReplace(this, other)
    case other: ArrayMoveOperation    => ArrayOperationTransformer.transformRemoveMove(this, other)
    case other: ArraySetOperation     => ArrayOperationTransformer.transformRemoveSet(this, other)
  }
}

object ArrayRemoveOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int, var oldValue: JsonNode) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ArrayRemoveOperation = ArrayRemoveOperation(path, noOp, index, oldValue)
  }
}

case class ArrayReplaceOperation(override val path: List[Any], override val noOp: Boolean, index: Int, oldValue: JsonNode, newValue: JsonNode) extends DiscreteOperation(path, noOp) with ArrayOperation {

  def invert(): DiscreteOperation = ArrayReplaceOperation(path, noOp, index, newValue, oldValue)

  def copyBuilder(): ArrayReplaceOperation.Builder = new ArrayReplaceOperation.Builder(path, noOp, index, oldValue, newValue)

  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ArrayInsertOperation  => ArrayOperationTransformer.transformReplaceInsert(this, other)
    case other: ArrayRemoveOperation  => ArrayOperationTransformer.transformReplaceRemove(this, other)
    case other: ArrayReplaceOperation => ArrayOperationTransformer.transformReplaceReplace(this, other)
    case other: ArrayMoveOperation    => ArrayOperationTransformer.transformReplaceMove(this, other)
    case other: ArraySetOperation     => ArrayOperationTransformer.transformReplaceSet(this, other)
  }
}

object ArrayReplaceOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int, var oldValue: JsonNode, var newValue: JsonNode) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ArrayReplaceOperation = ArrayReplaceOperation(path, noOp, index, oldValue, newValue)
  }
}

case class ArrayMoveOperation(override val path: List[Any], override val noOp: Boolean, index: Int, fromIndex: Int, toIndex: Int) extends DiscreteOperation(path, noOp) with ArrayOperation {

  def invert(): DiscreteOperation = ArrayMoveOperation(path, noOp, index, toIndex, fromIndex)

  def copyBuilder(): ArrayMoveOperation.Builder = new ArrayMoveOperation.Builder(path, noOp, index, fromIndex, toIndex)

  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ArrayInsertOperation  => ArrayOperationTransformer.transformMoveInsert(this, other)
    case other: ArrayRemoveOperation  => ArrayOperationTransformer.transformMoveRemove(this, other)
    case other: ArrayReplaceOperation => ArrayOperationTransformer.transformMoveReplace(this, other)
    case other: ArrayMoveOperation    => ArrayOperationTransformer.transformMoveMove(this, other)
    case other: ArraySetOperation     => ArrayOperationTransformer.transformMoveSet(this, other)
  }
}

object ArrayMoveOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int, var fromIndex: Int, var toIndex: Int) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ArrayMoveOperation = ArrayMoveOperation(path, noOp, index, fromIndex, toIndex)
  }
}

case class ArraySetOperation(override val path: List[Any], override val noOp: Boolean, oldValue: List[Any], newValue: List[Any]) extends DiscreteOperation(path, noOp) with ArrayOperation {

  def invert(): DiscreteOperation = ArraySetOperation(path, noOp, newValue, oldValue)

  def copyBuilder(): ArraySetOperation.Builder = new ArraySetOperation.Builder(path, noOp, oldValue, newValue)

  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ArrayInsertOperation  => ArrayOperationTransformer.transformSetInsert(this, other)
    case other: ArrayRemoveOperation  => ArrayOperationTransformer.transformSetRemove(this, other)
    case other: ArrayReplaceOperation => ArrayOperationTransformer.transformSetReplace(this, other)
    case other: ArrayMoveOperation    => ArrayOperationTransformer.transformSetMove(this, other)
    case other: ArraySetOperation     => ArrayOperationTransformer.transformSetSet(this, other)
  }
}

object ArraySetOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var oldValue: List[Any], var newValue: List[Any]) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ArraySetOperation = ArraySetOperation(path, noOp, oldValue, newValue)
  }
}



