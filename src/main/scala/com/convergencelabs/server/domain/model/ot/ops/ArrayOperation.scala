package com.convergencelabs.server.domain.model.ot.ops

import com.convergencelabs.server.domain.model.ot.xform.ArrayOperationTransformer
import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JArray

sealed trait ArrayOperation extends DiscreteOperation {
  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation)
}

case class ArrayInsertOperation(path: List[Any], noOp: Boolean, index: Int, value: JValue) extends ArrayOperation {

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
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int, var value: JValue) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ArrayInsertOperation = ArrayInsertOperation(path, noOp, index, value)
  }
}

case class ArrayRemoveOperation(path: List[Any], noOp: Boolean, index: Int) extends ArrayOperation {

  def copyBuilder(): ArrayRemoveOperation.Builder = new ArrayRemoveOperation.Builder(path, noOp, index)

  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ArrayInsertOperation  => ArrayOperationTransformer.transformRemoveInsert(this, other)
    case other: ArrayRemoveOperation  => ArrayOperationTransformer.transformRemoveRemove(this, other)
    case other: ArrayReplaceOperation => ArrayOperationTransformer.transformRemoveReplace(this, other)
    case other: ArrayMoveOperation    => ArrayOperationTransformer.transformRemoveMove(this, other)
    case other: ArraySetOperation     => ArrayOperationTransformer.transformRemoveSet(this, other)
  }
}

object ArrayRemoveOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ArrayRemoveOperation = ArrayRemoveOperation(path, noOp, index)
  }
}

case class ArrayReplaceOperation(path: List[Any], noOp: Boolean, index: Int, newValue: JValue) extends ArrayOperation {

  def copyBuilder(): ArrayReplaceOperation.Builder = new ArrayReplaceOperation.Builder(path, noOp, index, newValue)

  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ArrayInsertOperation  => ArrayOperationTransformer.transformReplaceInsert(this, other)
    case other: ArrayRemoveOperation  => ArrayOperationTransformer.transformReplaceRemove(this, other)
    case other: ArrayReplaceOperation => ArrayOperationTransformer.transformReplaceReplace(this, other)
    case other: ArrayMoveOperation    => ArrayOperationTransformer.transformReplaceMove(this, other)
    case other: ArraySetOperation     => ArrayOperationTransformer.transformReplaceSet(this, other)
  }
}

object ArrayReplaceOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var index: Int, var newValue: JValue) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ArrayReplaceOperation = ArrayReplaceOperation(path, noOp, index, newValue)
  }
}

case class ArrayMoveOperation(path: List[Any], noOp: Boolean, fromIndex: Int, toIndex: Int) extends ArrayOperation {

  def copyBuilder(): ArrayMoveOperation.Builder = new ArrayMoveOperation.Builder(path, noOp, fromIndex, toIndex)

  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ArrayInsertOperation  => ArrayOperationTransformer.transformMoveInsert(this, other)
    case other: ArrayRemoveOperation  => ArrayOperationTransformer.transformMoveRemove(this, other)
    case other: ArrayReplaceOperation => ArrayOperationTransformer.transformMoveReplace(this, other)
    case other: ArrayMoveOperation    => ArrayOperationTransformer.transformMoveMove(this, other)
    case other: ArraySetOperation     => ArrayOperationTransformer.transformMoveSet(this, other)
  }
}

object ArrayMoveOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var fromIndex: Int, var toIndex: Int) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ArrayMoveOperation = ArrayMoveOperation(path, noOp, fromIndex, toIndex)
  }
}

case class ArraySetOperation(path: List[Any], noOp: Boolean, newValue: JArray) extends ArrayOperation {

  def copyBuilder(): ArraySetOperation.Builder = new ArraySetOperation.Builder(path, noOp, newValue)

  def transform(other: ArrayOperation): (DiscreteOperation, DiscreteOperation) = other match {
    case other: ArrayInsertOperation  => ArrayOperationTransformer.transformSetInsert(this, other)
    case other: ArrayRemoveOperation  => ArrayOperationTransformer.transformSetRemove(this, other)
    case other: ArrayReplaceOperation => ArrayOperationTransformer.transformSetReplace(this, other)
    case other: ArrayMoveOperation    => ArrayOperationTransformer.transformSetMove(this, other)
    case other: ArraySetOperation     => ArrayOperationTransformer.transformSetSet(this, other)
  }
}

object ArraySetOperation {
  class Builder(path: List[Any], noOp: scala.Boolean, var newValue: JArray) extends DiscreteOperation.Builder(path, noOp) {
    def build(): ArraySetOperation = ArraySetOperation(path, noOp, newValue)
  }
}
