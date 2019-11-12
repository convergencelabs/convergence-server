/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.ot

import java.time.Instant

import com.convergencelabs.server.domain.model.data.DataValue

sealed trait Operation

case class CompoundOperation(operations: List[DiscreteOperation]) extends Operation

sealed trait DiscreteOperation extends Operation {
  def id: String
  def noOp: Boolean
  def clone(noOp: scala.Boolean = noOp): DiscreteOperation
}

///////////////////////////////////////////////////////////////////////////////
// String Operations
//////////////////////////////////////////////////////////////////////////////

sealed trait StringOperation extends DiscreteOperation
case class StringRemoveOperation(id: String, noOp: Boolean, index: Int, value: String) extends StringOperation {
  def clone(noOp: scala.Boolean = noOp): StringRemoveOperation = copy(noOp = noOp)
}

case class StringInsertOperation(id: String, noOp: Boolean, index: Int, value: String) extends StringOperation {
  def clone(noOp: scala.Boolean = noOp): StringInsertOperation = copy(noOp = noOp)
}

case class StringSetOperation(id: String, noOp: Boolean, value: String) extends StringOperation {
  def clone(noOp: scala.Boolean = noOp): StringSetOperation = copy(noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Object Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait ObjectOperation extends DiscreteOperation

case class ObjectSetPropertyOperation(id: String, noOp: Boolean, property: String, value: DataValue) extends ObjectOperation {
  def clone(noOp: scala.Boolean = noOp): ObjectSetPropertyOperation = copy(noOp = noOp)
}

case class ObjectAddPropertyOperation(id: String, noOp: Boolean, property: String, value: DataValue) extends ObjectOperation {
  def clone(noOp: scala.Boolean = noOp): ObjectAddPropertyOperation = copy(noOp = noOp)
}

case class ObjectRemovePropertyOperation(id: String, noOp: Boolean, property: String) extends ObjectOperation {
  def clone(noOp: scala.Boolean = noOp): ObjectRemovePropertyOperation = copy(noOp = noOp)
}

case class ObjectSetOperation(id: String, noOp: Boolean, value: Map[String, DataValue]) extends ObjectOperation {
  def clone(noOp: scala.Boolean = noOp): ObjectSetOperation = copy(noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Number Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait NumberOperation extends DiscreteOperation

case class NumberAddOperation(id: String, noOp: Boolean, value: Double) extends NumberOperation {
  def clone(noOp: scala.Boolean = noOp): NumberAddOperation = copy(noOp = noOp)
}

case class NumberSetOperation(id: String, noOp: Boolean, value: Double) extends NumberOperation {
  def clone(noOp: scala.Boolean = noOp): NumberSetOperation = copy(noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Boolean Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait BooleanOperation extends DiscreteOperation

case class BooleanSetOperation(id: String, noOp: Boolean, value: Boolean) extends BooleanOperation {
  def clone(noOp: scala.Boolean = noOp): BooleanSetOperation = copy(noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Array Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait ArrayOperation extends DiscreteOperation
case class ArrayInsertOperation(id: String, noOp: Boolean, index: Int, value: DataValue) extends ArrayOperation {
  def clone(noOp: scala.Boolean = noOp): ArrayInsertOperation = copy(noOp = noOp)
}

case class ArrayRemoveOperation(id: String, noOp: Boolean, index: Int) extends ArrayOperation {
  def clone(noOp: scala.Boolean = noOp): ArrayRemoveOperation = copy(noOp = noOp)
}

case class ArrayReplaceOperation(id: String, noOp: Boolean, index: Int, value: DataValue) extends ArrayOperation {
  def clone(noOp: scala.Boolean = noOp): ArrayReplaceOperation = copy(noOp = noOp)
}

case class ArrayMoveOperation(id: String, noOp: Boolean, fromIndex: Int, toIndex: Int) extends ArrayOperation {
  def clone(noOp: scala.Boolean = noOp): ArrayMoveOperation = copy(noOp = noOp)
}

case class ArraySetOperation(id: String, noOp: Boolean, value: List[DataValue]) extends ArrayOperation {
  def clone(noOp: scala.Boolean = noOp): ArraySetOperation = copy(noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// DAte Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait DateOperation extends DiscreteOperation

case class DateSetOperation(id: String, noOp: Boolean, value: Instant) extends DateOperation {
  def clone(noOp: scala.Boolean = noOp): DateSetOperation = copy(noOp = noOp)
}

 
sealed trait AppliedOperation 
 
case class AppliedCompoundOperation(operations: List[AppliedDiscreteOperation]) extends AppliedOperation 
 
sealed trait AppliedDiscreteOperation extends AppliedOperation { 
  def id: String 
  def noOp: Boolean
} 
 
/////////////////////////////////////////////////////////////////////////////// 
// String Operations 
////////////////////////////////////////////////////////////////////////////// 
 
sealed trait AppliedStringOperation extends AppliedDiscreteOperation
 
case class AppliedStringRemoveOperation(id: String, noOp: Boolean, index: Int, length: Int, oldValue: Option[String]) extends AppliedStringOperation 
case class AppliedStringInsertOperation(id: String, noOp: Boolean, index: Int, value: String) extends AppliedStringOperation
case class AppliedStringSetOperation(id: String, noOp: Boolean, value: String, oldValue: Option[String]) extends AppliedStringOperation
 
/////////////////////////////////////////////////////////////////////////////// 
// Object Operations 
////////////////////////////////////////////////////////////////////////////// 
sealed trait AppliedObjectOperation extends AppliedDiscreteOperation
 
case class AppliedObjectSetPropertyOperation(id: String, noOp: Boolean, property: String, value: DataValue, oldValue: Option[DataValue]) extends AppliedObjectOperation  
case class AppliedObjectAddPropertyOperation(id: String, noOp: Boolean, property: String, value: DataValue) extends AppliedObjectOperation  
case class AppliedObjectRemovePropertyOperation(id: String, noOp: Boolean, property: String, oldValue: Option[DataValue]) extends AppliedObjectOperation 
case class AppliedObjectSetOperation(id: String, noOp: Boolean, value: Map[String, DataValue], oldValue: Option[Map[String, DataValue]]) extends AppliedObjectOperation 
 
/////////////////////////////////////////////////////////////////////////////// 
// Number Operations 
////////////////////////////////////////////////////////////////////////////// 
sealed trait AppliedNumberOperation extends AppliedDiscreteOperation 
 
case class AppliedNumberAddOperation(id: String, noOp: Boolean, value: Double) extends AppliedNumberOperation 
case class AppliedNumberSetOperation(id: String, noOp: Boolean, value: Double, oldValue: Option[Double]) extends AppliedNumberOperation 
 
/////////////////////////////////////////////////////////////////////////////// 
// Boolean Operations 
////////////////////////////////////////////////////////////////////////////// 
sealed trait AppliedBooleanOperation extends AppliedDiscreteOperation  
 
case class AppliedBooleanSetOperation(id: String, noOp: Boolean, value: Boolean, oldValue: Option[Boolean]) extends AppliedBooleanOperation 
 
/////////////////////////////////////////////////////////////////////////////// 
// Array Operations 
////////////////////////////////////////////////////////////////////////////// 
sealed trait AppliedArrayOperation extends AppliedDiscreteOperation  
 
case class AppliedArrayInsertOperation(id: String, noOp: Boolean, index: Int, value: DataValue) extends AppliedArrayOperation 
case class AppliedArrayRemoveOperation(id: String, noOp: Boolean, index: Int, oldValue: Option[DataValue]) extends AppliedArrayOperation 
case class AppliedArrayReplaceOperation(id: String, noOp: Boolean, index: Int, value: DataValue, oldValue: Option[DataValue]) extends AppliedArrayOperation 
case class AppliedArrayMoveOperation(id: String, noOp: Boolean, fromIndex: Int, toIndex: Int) extends AppliedArrayOperation  
case class AppliedArraySetOperation(id: String, noOp: Boolean, value: List[DataValue], oldValue: Option[List[DataValue]]) extends AppliedArrayOperation 

/////////////////////////////////////////////////////////////////////////////// 
// Date Operations 
////////////////////////////////////////////////////////////////////////////// 
 
sealed trait AppliedDateOperation extends AppliedDiscreteOperation
case class AppliedDateSetOperation(id: String, noOp: Boolean, value: Instant, oldValue: Option[Instant]) extends AppliedDateOperation

