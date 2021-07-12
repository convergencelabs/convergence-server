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
  new JsonSubTypes.Type(value = classOf[AppliedDiscreteOperation], name = "compound"),
  new JsonSubTypes.Type(value = classOf[AppliedCompoundOperation], name = "discrete"),
))
sealed trait AppliedOperation

final case class AppliedCompoundOperation(operations: List[AppliedDiscreteOperation]) extends AppliedOperation

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[AppliedArrayInsertOperation], name = "array_insert"),
  new JsonSubTypes.Type(value = classOf[AppliedArrayRemoveOperation], name = "array_remove"),
  new JsonSubTypes.Type(value = classOf[AppliedArrayReplaceOperation], name = "array_replace"),
  new JsonSubTypes.Type(value = classOf[AppliedArrayMoveOperation], name = "array_move"),
  new JsonSubTypes.Type(value = classOf[AppliedArraySetOperation], name = "array_set"),

  new JsonSubTypes.Type(value = classOf[AppliedObjectAddPropertyOperation], name = "object_add_prop"),
  new JsonSubTypes.Type(value = classOf[AppliedObjectSetPropertyOperation], name = "object_set_prop"),
  new JsonSubTypes.Type(value = classOf[AppliedObjectRemovePropertyOperation], name = "object_remove_prop"),
  new JsonSubTypes.Type(value = classOf[AppliedObjectSetOperation], name = "object_set"),

  new JsonSubTypes.Type(value = classOf[AppliedStringSpliceOperation], name = "string_splice"),
  new JsonSubTypes.Type(value = classOf[AppliedStringSetOperation], name = "string_set"),

  new JsonSubTypes.Type(value = classOf[AppliedNumberAddOperation], name = "number_delta"),
  new JsonSubTypes.Type(value = classOf[AppliedNumberSetOperation], name = "number_set"),

  new JsonSubTypes.Type(value = classOf[AppliedBooleanSetOperation], name = "boolean_set"),

  new JsonSubTypes.Type(value = classOf[AppliedDateSetOperation], name = "date_set"),
))
sealed trait AppliedDiscreteOperation extends AppliedOperation {
  def id: String

  def noOp: Boolean
}

/////////////////////////////////////////////////////////////////////////////// 
// String Operations 
////////////////////////////////////////////////////////////////////////////// 

sealed trait AppliedStringOperation extends AppliedDiscreteOperation

final case class AppliedStringSpliceOperation(id: String, noOp: Boolean, index: Int, deleteCount: Int, deletedValue: Option[String], insertedValue: String) extends AppliedStringOperation

final case class AppliedStringSetOperation(id: String, noOp: Boolean, value: String, oldValue: Option[String]) extends AppliedStringOperation

/////////////////////////////////////////////////////////////////////////////// 
// Object Operations 
////////////////////////////////////////////////////////////////////////////// 
sealed trait AppliedObjectOperation extends AppliedDiscreteOperation

final case class AppliedObjectSetPropertyOperation(id: String, noOp: Boolean, property: String, value: DataValue, oldValue: Option[DataValue]) extends AppliedObjectOperation

final case class AppliedObjectAddPropertyOperation(id: String, noOp: Boolean, property: String, value: DataValue) extends AppliedObjectOperation

final case class AppliedObjectRemovePropertyOperation(id: String, noOp: Boolean, property: String, oldValue: Option[DataValue]) extends AppliedObjectOperation

final case class AppliedObjectSetOperation(id: String, noOp: Boolean, value: Map[String, DataValue], oldValue: Option[Map[String, DataValue]]) extends AppliedObjectOperation

/////////////////////////////////////////////////////////////////////////////// 
// Number Operations 
////////////////////////////////////////////////////////////////////////////// 
sealed trait AppliedNumberOperation extends AppliedDiscreteOperation

final case class AppliedNumberAddOperation(id: String, noOp: Boolean, value: Double) extends AppliedNumberOperation

final case class AppliedNumberSetOperation(id: String, noOp: Boolean, value: Double, oldValue: Option[Double]) extends AppliedNumberOperation

/////////////////////////////////////////////////////////////////////////////// 
// Boolean Operations 
////////////////////////////////////////////////////////////////////////////// 
sealed trait AppliedBooleanOperation extends AppliedDiscreteOperation

final case class AppliedBooleanSetOperation(id: String, noOp: Boolean, value: Boolean, oldValue: Option[Boolean]) extends AppliedBooleanOperation

/////////////////////////////////////////////////////////////////////////////// 
// Array Operations 
////////////////////////////////////////////////////////////////////////////// 
sealed trait AppliedArrayOperation extends AppliedDiscreteOperation

final case class AppliedArrayInsertOperation(id: String, noOp: Boolean, index: Int, value: DataValue) extends AppliedArrayOperation

final case class AppliedArrayRemoveOperation(id: String, noOp: Boolean, index: Int, oldValue: Option[DataValue]) extends AppliedArrayOperation

final case class AppliedArrayReplaceOperation(id: String, noOp: Boolean, index: Int, value: DataValue, oldValue: Option[DataValue]) extends AppliedArrayOperation

final case class AppliedArrayMoveOperation(id: String, noOp: Boolean, fromIndex: Int, toIndex: Int) extends AppliedArrayOperation

final case class AppliedArraySetOperation(id: String, noOp: Boolean, value: List[DataValue], oldValue: Option[List[DataValue]]) extends AppliedArrayOperation

/////////////////////////////////////////////////////////////////////////////// 
// Date Operations 
////////////////////////////////////////////////////////////////////////////// 

sealed trait AppliedDateOperation extends AppliedDiscreteOperation

final case class AppliedDateSetOperation(id: String, noOp: Boolean, value: Instant, oldValue: Option[Instant]) extends AppliedDateOperation

