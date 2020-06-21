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

package com.convergencelabs.convergence.server.domain.model.data

import java.time.Instant

import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[ObjectValue], name = "object"),
  new JsonSubTypes.Type(value = classOf[ArrayValue], name = "array"),
  new JsonSubTypes.Type(value = classOf[BooleanValue], name = "boolean"),
  new JsonSubTypes.Type(value = classOf[DoubleValue], name = "date"),
  new JsonSubTypes.Type(value = classOf[DateValue], name = "double"),
  new JsonSubTypes.Type(value = classOf[NullValue], name = "null"),
  new JsonSubTypes.Type(value = classOf[StringValue], name = "string"),

))
sealed trait DataValue {
  val id: String
}

case class ArrayValue(id: String, children: List[DataValue]) extends DataValue

case class BooleanValue(id: String, value: Boolean) extends DataValue

case class DoubleValue(id: String, value: Double) extends DataValue

case class DateValue(id: String, value: Instant) extends DataValue

case class NullValue(id: String) extends DataValue

case class ObjectValue(id: String, children: Map[String, DataValue]) extends DataValue

case class StringValue(id: String, value: String) extends DataValue

