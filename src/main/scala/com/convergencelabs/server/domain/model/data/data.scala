/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.data

import java.time.Instant

sealed trait DataValue {
  val id: String
}

case class ObjectValue(id: String, children: Map[String, DataValue]) extends DataValue

case class ArrayValue(id: String, children: List[DataValue]) extends DataValue

case class BooleanValue(id: String, value: Boolean) extends DataValue

case class DoubleValue(id: String, value: Double) extends DataValue

case class NullValue(id: String) extends DataValue

case class StringValue(id: String, value: String) extends DataValue

case class DateValue(id: String, value: Instant) extends DataValue