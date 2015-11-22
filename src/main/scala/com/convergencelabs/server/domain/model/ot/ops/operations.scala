package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JValue

sealed trait Operation

case class CompoundOperation(operations: List[DiscreteOperation]) extends Operation

sealed trait DiscreteOperation extends Operation {
  def path: List[Any]
  def noOp: Boolean

  def isParentOf(other: DiscreteOperation) = PathComparator.isParentOf(path, other.path)
  def isAncestorOf(other: DiscreteOperation) = PathComparator.isAncestorOf(path, other.path)
  def isChildOf(other: DiscreteOperation) = PathComparator.isChildOf(path, other.path)
  def isDescendantOf(other: DiscreteOperation) = PathComparator.isDescendantOf(path, other.path)
  def isSiblingOf(other: DiscreteOperation) = PathComparator.areSiblings(path, other.path)

  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): DiscreteOperation
}

///////////////////////////////////////////////////////////////////////////////
// String Operations
//////////////////////////////////////////////////////////////////////////////

sealed trait StringOperation extends DiscreteOperation
case class StringRemoveOperation(path: List[Any], noOp: Boolean, index: Int, value: String) extends StringOperation {
  def clone(path: List[Any], noOp: scala.Boolean): StringRemoveOperation = copy(path = path, noOp = noOp)
}

case class StringInsertOperation(path: List[Any], noOp: Boolean, index: Int, value: String) extends StringOperation {
  def clone(path: List[Any], noOp: scala.Boolean): StringInsertOperation = copy(path = path, noOp = noOp)
}

case class StringSetOperation(path: List[Any], noOp: Boolean, value: String) extends StringOperation {
  def clone(path: List[Any], noOp: scala.Boolean): StringSetOperation = copy(path = path, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Object Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait ObjectOperation extends DiscreteOperation

case class ObjectSetPropertyOperation(path: List[Any], noOp: Boolean, property: String, newValue: JValue) extends ObjectOperation {
  def clone(path: List[Any], noOp: scala.Boolean): ObjectSetPropertyOperation = copy(path = path, noOp = noOp)
}

case class ObjectAddPropertyOperation(path: List[Any], noOp: Boolean, property: String, newValue: JValue) extends ObjectOperation {
  def clone(path: List[Any], noOp: scala.Boolean): ObjectAddPropertyOperation = copy(path = path, noOp = noOp)
}

case class ObjectRemovePropertyOperation(path: List[Any], noOp: Boolean, property: String) extends ObjectOperation {
  def clone(path: List[Any], noOp: scala.Boolean): ObjectRemovePropertyOperation = copy(path = path, noOp = noOp)
}

case class ObjectSetOperation(path: List[Any], noOp: Boolean, newValue: JObject) extends ObjectOperation {
  def clone(path: List[Any], noOp: scala.Boolean): ObjectSetOperation = copy(path = path, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Number Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait NumberOperation extends DiscreteOperation

case class NumberAddOperation(path: List[Any], noOp: Boolean, value: JNumber) extends NumberOperation {
  def clone(path: List[Any], noOp: scala.Boolean): NumberAddOperation = copy(path = path, noOp = noOp)
}

case class NumberSetOperation(path: List[Any], noOp: Boolean, newValue: JNumber) extends NumberOperation {
  def clone(path: List[Any], noOp: scala.Boolean): NumberSetOperation = copy(path = path, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Array Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait ArrayOperation extends DiscreteOperation
case class ArrayInsertOperation(path: List[Any], noOp: Boolean, index: Int, value: JValue) extends ArrayOperation {
  def clone(path: List[Any], noOp: scala.Boolean): ArrayInsertOperation = copy(path = path, noOp = noOp)
}

case class ArrayRemoveOperation(path: List[Any], noOp: Boolean, index: Int) extends ArrayOperation {
  def clone(path: List[Any], noOp: scala.Boolean): ArrayRemoveOperation = copy(path = path, noOp = noOp)
}

case class ArrayReplaceOperation(path: List[Any], noOp: Boolean, index: Int, newValue: JValue) extends ArrayOperation {
  def clone(path: List[Any], noOp: scala.Boolean): ArrayReplaceOperation = copy(path = path, noOp = noOp)
}

case class ArrayMoveOperation(path: List[Any], noOp: Boolean, fromIndex: Int, toIndex: Int) extends ArrayOperation {
  def clone(path: List[Any], noOp: scala.Boolean): ArrayMoveOperation = copy(path = path, noOp = noOp)
}

case class ArraySetOperation(path: List[Any], noOp: Boolean, newValue: JArray) extends ArrayOperation {
  def clone(path: List[Any], noOp: scala.Boolean): ArraySetOperation = copy(path = path, noOp = noOp)
}