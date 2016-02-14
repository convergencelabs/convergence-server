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
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): DiscreteOperation
}

///////////////////////////////////////////////////////////////////////////////
// String Operations
//////////////////////////////////////////////////////////////////////////////

sealed trait StringOperation extends DiscreteOperation
case class StringRemoveOperation(path: List[Any], noOp: Boolean, index: Int, value: String) extends StringOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): StringRemoveOperation = copy(path = path, noOp = noOp)
}

case class StringInsertOperation(path: List[Any], noOp: Boolean, index: Int, value: String) extends StringOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): StringInsertOperation = copy(path = path, noOp = noOp)
}

case class StringSetOperation(path: List[Any], noOp: Boolean, value: String) extends StringOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): StringSetOperation = copy(path = path, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Object Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait ObjectOperation extends DiscreteOperation

case class ObjectSetPropertyOperation(path: List[Any], noOp: Boolean, property: String, value: JValue) extends ObjectOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): ObjectSetPropertyOperation = copy(path = path, noOp = noOp)
}

case class ObjectAddPropertyOperation(path: List[Any], noOp: Boolean, property: String, value: JValue) extends ObjectOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): ObjectAddPropertyOperation = copy(path = path, noOp = noOp)
}

case class ObjectRemovePropertyOperation(path: List[Any], noOp: Boolean, property: String) extends ObjectOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): ObjectRemovePropertyOperation = copy(path = path, noOp = noOp)
}

case class ObjectSetOperation(path: List[Any], noOp: Boolean, value: JObject) extends ObjectOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): ObjectSetOperation = copy(path = path, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Number Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait NumberOperation extends DiscreteOperation

case class NumberAddOperation(path: List[Any], noOp: Boolean, value: JNumber) extends NumberOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): NumberAddOperation = copy(path = path, noOp = noOp)
}

case class NumberSetOperation(path: List[Any], noOp: Boolean, value: JNumber) extends NumberOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): NumberSetOperation = copy(path = path, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Boolean Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait BooleanOperation extends DiscreteOperation

case class BooleanSetOperation(path: List[Any], noOp: Boolean, value: Boolean) extends BooleanOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): BooleanSetOperation = copy(path = path, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Array Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait ArrayOperation extends DiscreteOperation
case class ArrayInsertOperation(path: List[Any], noOp: Boolean, index: Int, value: JValue) extends ArrayOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): ArrayInsertOperation = copy(path = path, noOp = noOp)
}

case class ArrayRemoveOperation(path: List[Any], noOp: Boolean, index: Int) extends ArrayOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): ArrayRemoveOperation = copy(path = path, noOp = noOp)
}

case class ArrayReplaceOperation(path: List[Any], noOp: Boolean, index: Int, value: JValue) extends ArrayOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): ArrayReplaceOperation = copy(path = path, noOp = noOp)
}

case class ArrayMoveOperation(path: List[Any], noOp: Boolean, fromIndex: Int, toIndex: Int) extends ArrayOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): ArrayMoveOperation = copy(path = path, noOp = noOp)
}

case class ArraySetOperation(path: List[Any], noOp: Boolean, value: JArray) extends ArrayOperation {
  def clone(path: List[Any] = path, noOp: scala.Boolean = noOp): ArraySetOperation = copy(path = path, noOp = noOp)
}
