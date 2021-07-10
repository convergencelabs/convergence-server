/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.services.domain.model.ot

import java.time.Instant

import com.convergencelabs.convergence.server.model.domain.model.DataValue
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[CompoundOperation], name = "compound"),
  new JsonSubTypes.Type(value = classOf[DiscreteOperation], name = "discrete")
))
sealed trait Operation

final case class CompoundOperation(operations: List[DiscreteOperation]) extends Operation

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[ArrayInsertOperation], name = "array_insert"),
  new JsonSubTypes.Type(value = classOf[ArrayRemoveOperation], name = "array_remove"),
  new JsonSubTypes.Type(value = classOf[ArrayReplaceOperation], name = "array_replace"),
  new JsonSubTypes.Type(value = classOf[ArrayMoveOperation], name = "array_move"),
  new JsonSubTypes.Type(value = classOf[ArraySetOperation], name = "array_set"),

  new JsonSubTypes.Type(value = classOf[ObjectAddPropertyOperation], name = "object_add_prop"),
  new JsonSubTypes.Type(value = classOf[ObjectSetPropertyOperation], name = "object_set_prop"),
  new JsonSubTypes.Type(value = classOf[ObjectRemovePropertyOperation], name = "object_remove_prop"),
  new JsonSubTypes.Type(value = classOf[ObjectSetOperation], name = "object_set"),

  new JsonSubTypes.Type(value = classOf[StringSpliceOperation], name = "string_splice"),
  new JsonSubTypes.Type(value = classOf[StringSetOperation], name = "string_set"),

  new JsonSubTypes.Type(value = classOf[NumberAddOperation], name = "number_delta"),
  new JsonSubTypes.Type(value = classOf[NumberSetOperation], name = "number_set"),

  new JsonSubTypes.Type(value = classOf[BooleanSetOperation], name = "boolean_set"),

  new JsonSubTypes.Type(value = classOf[DateSetOperation], name = "date_set"),
))
sealed trait DiscreteOperation extends Operation {
  def id: String

  def noOp: Boolean

  def clone(noOp: scala.Boolean = noOp): DiscreteOperation
}

///////////////////////////////////////////////////////////////////////////////
// String Operations
//////////////////////////////////////////////////////////////////////////////

sealed trait StringOperation extends DiscreteOperation

final case class StringSpliceOperation(id: String, noOp: Boolean, index: Int, deleteCount: Int, insertValue: String) extends StringOperation {
  def clone(noOp: scala.Boolean = noOp): StringSpliceOperation = copy(noOp = noOp)
}

final case class StringSetOperation(id: String, noOp: Boolean, value: String) extends StringOperation {
  def clone(noOp: scala.Boolean = noOp): StringSetOperation = copy(noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Object Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait ObjectOperation extends DiscreteOperation

final case class ObjectSetPropertyOperation(id: String, noOp: Boolean, property: String, value: DataValue) extends ObjectOperation {
  def clone(noOp: scala.Boolean = noOp): ObjectSetPropertyOperation = copy(noOp = noOp)
}

final case class ObjectAddPropertyOperation(id: String, noOp: Boolean, property: String, value: DataValue) extends ObjectOperation {
  def clone(noOp: scala.Boolean = noOp): ObjectAddPropertyOperation = copy(noOp = noOp)
}

final case class ObjectRemovePropertyOperation(id: String, noOp: Boolean, property: String) extends ObjectOperation {
  def clone(noOp: scala.Boolean = noOp): ObjectRemovePropertyOperation = copy(noOp = noOp)
}

final case class ObjectSetOperation(id: String, noOp: Boolean, value: Map[String, DataValue]) extends ObjectOperation {
  def clone(noOp: scala.Boolean = noOp): ObjectSetOperation = copy(noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Number Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait NumberOperation extends DiscreteOperation

final case class NumberAddOperation(id: String, noOp: Boolean, value: Double) extends NumberOperation {
  def clone(noOp: scala.Boolean = noOp): NumberAddOperation = copy(noOp = noOp)
}

final case class NumberSetOperation(id: String, noOp: Boolean, value: Double) extends NumberOperation {
  def clone(noOp: scala.Boolean = noOp): NumberSetOperation = copy(noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Boolean Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait BooleanOperation extends DiscreteOperation

final case class BooleanSetOperation(id: String, noOp: Boolean, value: Boolean) extends BooleanOperation {
  def clone(noOp: scala.Boolean = noOp): BooleanSetOperation = copy(noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Array Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait ArrayOperation extends DiscreteOperation

final case class ArrayInsertOperation(id: String, noOp: Boolean, index: Int, value: DataValue) extends ArrayOperation {
  def clone(noOp: scala.Boolean = noOp): ArrayInsertOperation = copy(noOp = noOp)
}

final case class ArrayRemoveOperation(id: String, noOp: Boolean, index: Int) extends ArrayOperation {
  def clone(noOp: scala.Boolean = noOp): ArrayRemoveOperation = copy(noOp = noOp)
}

final case class ArrayReplaceOperation(id: String, noOp: Boolean, index: Int, value: DataValue) extends ArrayOperation {
  def clone(noOp: scala.Boolean = noOp): ArrayReplaceOperation = copy(noOp = noOp)
}

final case class ArrayMoveOperation(id: String, noOp: Boolean, fromIndex: Int, toIndex: Int) extends ArrayOperation {
  def clone(noOp: scala.Boolean = noOp): ArrayMoveOperation = copy(noOp = noOp)
}

final case class ArraySetOperation(id: String, noOp: Boolean, value: List[DataValue]) extends ArrayOperation {
  def clone(noOp: scala.Boolean = noOp): ArraySetOperation = copy(noOp = noOp)
}

///////////////////////////////////////////////////////////////////////////////
// Date Operations
//////////////////////////////////////////////////////////////////////////////
sealed trait DateOperation extends DiscreteOperation

final case class DateSetOperation(id: String, noOp: Boolean, value: Instant) extends DateOperation {
  def clone(noOp: scala.Boolean = noOp): DateSetOperation = copy(noOp = noOp)
}
