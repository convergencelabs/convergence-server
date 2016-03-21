package com.convergencelabs.server.domain.model.ot

import org.json4s.JsonAST.JArray
import org.json4s.JsonAST.JNumber
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.JValue
import org.json4s.JsonAST.JDouble

sealed trait Operation

case class CompoundOperation(operations: List[DiscreteOperation]) extends Operation

sealed trait DiscreteOperation extends Operation {
  def id: String
  def noOp: Boolean
  def clone(id: String = id, noOp: scala.Boolean = noOp): DiscreteOperation
}

///////////////////////////////////////////////////////////////////////////////
// String Operations
//////////////////////////////////////////////////////////////////////////////

sealed trait StringOperation extends DiscreteOperation
case class StringRemoveOperation(id: String, noOp: Boolean, index: Int, value: String) extends StringOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): StringRemoveOperation = copy(id = id, noOp = noOp)
}

case class StringInsertOperation(id: String, noOp: Boolean, index: Int, value: String) extends StringOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): StringInsertOperation = copy(id = id, noOp = noOp)
}

case class StringSetOperation(id: String, noOp: Boolean, value: String) extends StringOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): StringSetOperation = copy(id = id, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Object Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait ObjectOperation extends DiscreteOperation

case class ObjectSetPropertyOperation(id: String, noOp: Boolean, property: String, value: JValue) extends ObjectOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): ObjectSetPropertyOperation = copy(id = id, noOp = noOp)
}

case class ObjectAddPropertyOperation(id: String, noOp: Boolean, property: String, value: JValue) extends ObjectOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): ObjectAddPropertyOperation = copy(id = id, noOp = noOp)
}

case class ObjectRemovePropertyOperation(id: String, noOp: Boolean, property: String) extends ObjectOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): ObjectRemovePropertyOperation = copy(id = id, noOp = noOp)
}

case class ObjectSetOperation(id: String, noOp: Boolean, value: JObject) extends ObjectOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): ObjectSetOperation = copy(id = id, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Number Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait NumberOperation extends DiscreteOperation

case class NumberAddOperation(id: String, noOp: Boolean, value: JDouble) extends NumberOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): NumberAddOperation = copy(id = id, noOp = noOp)
}

case class NumberSetOperation(id: String, noOp: Boolean, value: JDouble) extends NumberOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): NumberSetOperation = copy(id = id, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Boolean Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait BooleanOperation extends DiscreteOperation

case class BooleanSetOperation(id: String, noOp: Boolean, value: Boolean) extends BooleanOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): BooleanSetOperation = copy(id = id, noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Array Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait ArrayOperation extends DiscreteOperation
case class ArrayInsertOperation(id: String, noOp: Boolean, index: Int, value: JValue) extends ArrayOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): ArrayInsertOperation = copy(id = id, noOp = noOp)
}

case class ArrayRemoveOperation(id: String, noOp: Boolean, index: Int) extends ArrayOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): ArrayRemoveOperation = copy(id = id, noOp = noOp)
}

case class ArrayReplaceOperation(id: String, noOp: Boolean, index: Int, value: JValue) extends ArrayOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): ArrayReplaceOperation = copy(id = id, noOp = noOp)
}

case class ArrayMoveOperation(id: String, noOp: Boolean, fromIndex: Int, toIndex: Int) extends ArrayOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): ArrayMoveOperation = copy(id = id, noOp = noOp)
}

case class ArraySetOperation(id: String, noOp: Boolean, value: JArray) extends ArrayOperation {
  def clone(id: String = id, noOp: scala.Boolean = noOp): ArraySetOperation = copy(id = id, noOp = noOp)
}
