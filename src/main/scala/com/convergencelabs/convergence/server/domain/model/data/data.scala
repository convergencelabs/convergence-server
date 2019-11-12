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